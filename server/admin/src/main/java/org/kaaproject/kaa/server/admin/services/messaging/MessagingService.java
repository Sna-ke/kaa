/*
 * Copyright 2014 CyberVision, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaaproject.kaa.server.admin.services.messaging;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Repository;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

@Repository("messagingService")
public class MessagingService {

    private static final Logger logger = LoggerFactory.getLogger(MessagingService.class);

    @Autowired
    private JavaMailSenderImpl kaaMessagingMailSender;

    @Autowired
    private MessageSource messages;

    private ExecutorService sendPool;

    private int sendPoolSize;

    private int sendTimeout;

    private String appBaseUrl;

    private String appName;

    private String mailFrom;

    public void setSendPoolSize(int sendPoolSize) {
        this.sendPoolSize = sendPoolSize;
    }

    public void setSendTimeout(int sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public MessagingService() {}

    public void init() {
        String sendName = "send-message-call-runner-%d";
        sendPool = Executors.newFixedThreadPool(sendPoolSize, new ThreadFactoryBuilder().setNameFormat(sendName).build());
    }

    public void destroy() {
        if (sendPool != null) {
            sendPool.shutdown();
            try {
                while (sendPool.isTerminated() == false) {
                    sendPool.awaitTermination(sendTimeout, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ex) {
                logger.warn("shutdown interrupted on {}", sendPool, ex);
            }
        }
    }

    public void sendTempPassword(final String username, final String password, final String email) {
        try {
            callAsync(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    String subject =  messages.getMessage("tempPasswordMailMessageSubject", new Object[]{appName}, Locale.ENGLISH);
                    String text = messages.getMessage("tempPasswordMailMessageBody", new Object[]{appBaseUrl, appName, username, password}, Locale.ENGLISH);
                    MimeMessage mimeMsg = kaaMessagingMailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMsg, "UTF-8");
                    try {
                        helper.setFrom(mailFrom);
                        helper.setTo(email);
                        helper.setSubject(subject);
                        helper.setText(text, true);
                        kaaMessagingMailSender.send(helper.getMimeMessage());
                    } catch (MessagingException e) {
                        logger.error("Unexpected error while sendTempPasswordMail", e);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            logger.error("Unexpected error while sendTempPasswordMail", e);
        }
    }

    public void sendPasswordAfterReset(final String username, final String password, final String email) {
        try {
            callAsync(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    String subject =  messages.getMessage("tempPasswordMailMessageSubject", new Object[]{appName}, Locale.ENGLISH);
                    String text = messages.getMessage("resetPasswordMailMessageBody", new Object[]{username, appBaseUrl, appName, password}, Locale.ENGLISH);
                    MimeMessage mimeMsg = kaaMessagingMailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMsg, "UTF-8");
                    try {
                        helper.setFrom(mailFrom);
                        helper.setTo(email);
                        helper.setSubject(subject);
                        helper.setText(text, true);
                        kaaMessagingMailSender.send(helper.getMimeMessage());
                    } catch (MessagingException e) {
                        logger.error("Unexpected error while sendPasswordAfterResetMail", e);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            logger.error("Unexpected error while sendPasswordAfterResetMail", e);
        }
    }

    public void sendPasswordResetLink(final String passwordReminderHash, final String username, final String email) {
        try {
            callAsync(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    String params = "#resetPassword=" + passwordReminderHash;
                    String subject =  messages.getMessage("recoveryPasswordMailMessageSubject", new Object[]{appName}, Locale.ENGLISH);
                    String text = messages.getMessage("recoveryPasswordMailMessageBody", new Object[]{username, appName, appBaseUrl+params}, Locale.ENGLISH);
                    MimeMessage mimeMsg = kaaMessagingMailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(mimeMsg, "UTF-8");
                    try {
                        helper.setFrom(mailFrom);
                        helper.setTo(email);
                        helper.setSubject(subject);
                        helper.setText(text, true);
                        kaaMessagingMailSender.send(helper.getMimeMessage());
                    } catch (MessagingException e) {
                        logger.error("Unexpected error while sendPasswordResetLinkMail", e);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            logger.error("Unexpected error while sendPasswordResetLinkMail", e);
        }
    }

    private <T> void callAsync(Callable<T> callable) throws IOException, InterruptedException {
        sendPool.submit(callable);
    }

    @SuppressWarnings("unused")
    private <T> T callWithTimeout(Callable<T> callable) throws IOException, InterruptedException {
        Future<T> future = sendPool.submit(callable);
        try {
            if (sendTimeout > 0) {
                return future.get(sendTimeout, TimeUnit.SECONDS);
            } else {
                return future.get();
            }
        } catch (TimeoutException eT) {
            future.cancel(true);
            throw new IOException("Callable timed out after " + sendTimeout
                    + " sec", eT);
        } catch (ExecutionException e1) {
            Throwable cause = e1.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof InterruptedException) {
                throw (InterruptedException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new RuntimeException(e1);
            }
        } catch (CancellationException ce) {
            throw new InterruptedException(
                    "Blocked callable interrupted by rotation event");
        } catch (InterruptedException ex) {
            logger.warn("Unexpected Exception {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}