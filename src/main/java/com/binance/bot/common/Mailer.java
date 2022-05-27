package com.binance.bot.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

@Component
public class Mailer{
  private static final Logger log = LoggerFactory.getLogger(Mailer.class);
  private static final String EMAIL_ADDRESS = "cryptoalertsforwalkingcorpse@gmail.com";
  @Value("${email_password}")
  private String emailPassword;
  public void sendEmail(String sub, String msg) throws MessagingException {
    //Get properties object
    Properties props = new Properties();
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.socketFactory.port", "465");
    props.put("mail.smtp.socketFactory.class",
        "javax.net.ssl.SSLSocketFactory");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.port", "465");
    //get Session
    Session session = Session.getDefaultInstance(props,
        new javax.mail.Authenticator() {
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(EMAIL_ADDRESS, emailPassword);
          }
        });
    //compose message
    MimeMessage message = new MimeMessage(session);
    message.addRecipient(Message.RecipientType.TO, new InternetAddress(EMAIL_ADDRESS));
    message.setSubject(sub);
    message.setText(msg == null? "null message": msg);
    //send message
    Transport.send(message);
  }

  public static void main(String args[]) throws MessagingException {
    Mailer mailer = new Mailer();
    mailer.sendEmail("test email", "test body");
  }
}
