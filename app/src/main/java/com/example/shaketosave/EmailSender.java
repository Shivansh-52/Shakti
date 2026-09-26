package com.example.shaketosave;

import android.os.AsyncTask;
import android.net.Uri;
import java.io.File;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class EmailSender extends AsyncTask<Void, Void, Boolean> {

    private final String senderEmail;
    private final String senderPassword;
    private final String recipientEmail;
    private final String subject;
    private final String messageBody;
    private final File attachmentFile;
    private final EmailCallback callback;
    private String errorMessage = "";

    public interface EmailCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public EmailSender(String senderEmail, String senderPassword, String recipientEmail,
                       String subject, String messageBody, File attachmentFile, EmailCallback callback) {
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.messageBody = messageBody;
        this.attachmentFile = attachmentFile;
        this.callback = callback;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "465");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderEmail.trim(), senderPassword.trim().replace(" ", ""));
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject(subject);

            // Create multipart message for attachment
            Multipart multipart = new MimeMultipart();

            // Add text message body
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(messageBody);
            multipart.addBodyPart(messageBodyPart);

            // Add video attachment if present
            if (attachmentFile != null && attachmentFile.exists()) {
                BodyPart attachmentPart = new MimeBodyPart();
                FileDataSource source = new FileDataSource(attachmentFile);
                attachmentPart.setDataHandler(new DataHandler(source));
                attachmentPart.setFileName("SOS_Video_" + attachmentFile.getName());
                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            errorMessage = e.getMessage();
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (callback != null) {
            if (success) {
                callback.onSuccess();
            } else {
                callback.onFailure(errorMessage);
            }
        }
    }
}
