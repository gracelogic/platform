package com.gracelogic.platform.notification.email;

import com.gracelogic.platform.notification.dto.Message;
import com.gracelogic.platform.property.service.PropertyService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Date;
import java.util.Properties;

@Service
public class EmailServiceImpl implements EmailService {
    private static Logger logger = Logger.getLogger(EmailServiceImpl.class);

    @Autowired
    private PropertyService propertyService;

    @Override
    public boolean sendMessage(Message message) {
        logger.info(String.format("Sending e-mail to: %s", message.getTo()));

        try {

            boolean isSslEnable = propertyService.getPropertyValueAsBoolean("notification:smtp_ssl_enable");
            boolean isAuth = propertyService.getPropertyValueAsBoolean("notification:smtp_auth");

            Properties p = new Properties();
            p.put("mail.smtp.host", propertyService.getPropertyValue("notification:smtp_host"));
            p.put("mail.smtp.port", propertyService.getPropertyValue("notification:smtp_port"));
            p.put("mail.smtp.auth", propertyService.getPropertyValue("notification:smtp_auth"));
            p.put("mail.smtp.ssl.enable", propertyService.getPropertyValue("notification:smtp_ssl_enable"));

            if (isSslEnable) {
                p.put("mail.smtp.socketFactory.port", propertyService.getPropertyValue("notification:smtp_socketFactory_port"));
                p.put("mail.smtp.socketFactory.class", propertyService.getPropertyValue("notification:smtp_socketFactory_class"));
            }

            Authenticator auth = null;
            if (isAuth) {
                auth = new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(propertyService.getPropertyValue("notification:smtp_user"), propertyService.getPropertyValue("notification:smtp_password"));
                    }
                };
            }

            Session s = Session.getInstance(p, auth);
            javax.mail.Message msg = new MimeMessage(s);
            msg.setFrom(new InternetAddress(message.getFrom()));
            for (String e : message.getToArr()) {
                msg.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(e));
            }
            msg.setSubject(message.getSubject());
            msg.setSentDate(new Date());
            MimeBodyPart bodyPart = new MimeBodyPart();
            Multipart body = new MimeMultipart();
            bodyPart.setText(message.getText(), "utf-8");
            body.addBodyPart(bodyPart);
            msg.setContent(body);

            Transport.send(msg);
        } catch (Exception e) {
            logger.error(String.format("Failed to send email to: %s.", message.getTo()), e);
            return false;
        }
        logger.info("Email send successfully");
        return true;
    }


}
