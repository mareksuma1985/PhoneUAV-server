package pl.bezzalogowe.PhoneUAV;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.Multipart;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.DataHandler;


public class GMail {

    /**
     * host POP3:
     * mail.active24.pl, pop3-bezzalogowe.nano.pl
     * port: without SSL - 110; z SSL - 995
     * <p>
     * host IMAP:
     * mail.active24.pl, imap-bezzalogowe.nano.pl
     * port: without SSL - 143; z SSL - 993
     * <p>
     * host SMTP:
     * mail.active24.pl, smtp-bezzalogowe.nano.pl
     * port: without SSL - 587; z SSL - 465
     * <p>
     * webmail:
     * https://poczta.a24.domeny.pl/
     */

    final String smtpAuth = "true";
    final String starttls = "true";

    String fromEmail;
    String fromPassword;
    String emailHost;
    int emailPort = 465;
    List<String> toEmailList;
    String emailSubject;
    String emailBody;

    Properties emailProperties;
    Session mailSession;
    MimeMessage emailMessage;
    MainActivity main;

    public GMail() {
    }

    public GMail(String emailHost, int emailPort, String fromEmail, String fromPassword, List<String> toEmailList, String emailSubject, String emailBody, MainActivity argActivity) {
        this.emailHost = emailHost;
        this.emailPort = emailPort;
        this.fromEmail = fromEmail;
        this.fromPassword = fromPassword;
        this.toEmailList = toEmailList;
        this.emailSubject = emailSubject;
        this.emailBody = emailBody;
        main = argActivity;

        emailProperties = System.getProperties();
        emailProperties.put("mail.smtp.port", emailPort);
        emailProperties.put("mail.smtp.auth", smtpAuth);
        emailProperties.put("mail.smtp.starttls.enable", starttls);
        Log.i("GMail", "Mail server properties set.");
    }

    public MimeMessage createEmailMessage() throws AddressException,
            MessagingException, UnsupportedEncodingException {

        mailSession = Session.getDefaultInstance(emailProperties, null);
        emailMessage = new MimeMessage(mailSession);

        emailMessage.setFrom(new InternetAddress(fromEmail, fromEmail));
        for (String toEmail : toEmailList) {
            Log.i("GMail", "toEmail: " + toEmail);
            emailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
        }

        emailMessage.setSubject(emailSubject);

        /* adding attachment */
        /** https://www.tutorialspoint.com/javamail_api/javamail_api_send_email_with_attachment.htm */

        BodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText(emailBody);
        //Log.i("GMail", "email body: " + emailBody);
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);
        BodyPart messageAttachmentPart = new MimeBodyPart();
        DataSource source = new FileDataSource(main.camObjectLolipop.imageFilePath);
        //Log.i("GMail", "attachment file path: " + main.camObjectLolipop.imageFilePath);
        messageAttachmentPart.setDataHandler(new DataHandler(source));
        messageAttachmentPart.setFileName(main.camObjectLolipop.imageFileName);
        multipart.addBodyPart(messageAttachmentPart);

        emailMessage.setContent(multipart);
        // for a email with attachment
        //emailMessage.setContent(emailBody, "text/html");
        // for a html email
        // emailMessage.setText(emailBody);
        // for a text email
        Log.i("GMail", "Email Message created.");
        return emailMessage;
    }

    public void sendEmail() throws AddressException, MessagingException {
        Transport transport = mailSession.getTransport("smtp");
        transport.connect(emailHost, fromEmail, fromPassword);
        Log.i("GMail", "allrecipients: " + emailMessage.getAllRecipients());
        transport.sendMessage(emailMessage, emailMessage.getAllRecipients());
        transport.close();
        Log.i("GMail", "Email sent successfully.");
    }
}
