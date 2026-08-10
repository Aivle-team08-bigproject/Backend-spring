package com.aivle.team08.backendspring.email.queue;

import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로컬 검증용 SMTP sender. Mailpit을 SES와 같은 EmailDeliverySender 계약으로 감싼다. */
@Component
@ConditionalOnProperty(name = "email.sender", havingValue = "mailpit", matchIfMissing = true)
public class MailpitEmailDeliverySender implements EmailDeliverySender {
    private final JavaMailSender mailSender;
    private final String from;

    public MailpitEmailDeliverySender(
            JavaMailSender mailSender,
            @Value("${email.from:no-reply@local.test}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public SendResult send(EmailDeliveryQueueMessage message) {
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.recipient());
            helper.setSubject("데이터 샘플 전달 (" + message.deliveryType() + ")");
            helper.setText(renderBody(message), true);
            mailSender.send(mimeMessage);
            return new SendResult("SENT", message.deliveryId(), null);
        } catch (MailException | jakarta.mail.MessagingException exception) {
            return new SendResult("FAILED", null, "SMTP_SEND_FAILED");
        }
    }

    private String renderBody(EmailDeliveryQueueMessage message) {
        String headers = message.sampleColumns().stream()
                .map(column -> escape(String.valueOf(column.getOrDefault("name", "column"))))
                .map(name -> "<th>" + name + "</th>")
                .collect(Collectors.joining());
        String rows = message.sampleRows().stream()
                .map(row -> row.values().stream()
                        .map(value -> "<td>" + escape(String.valueOf(value)) + "</td>")
                        .collect(Collectors.joining("", "<tr>", "</tr>")))
                .collect(Collectors.joining());
        return "<h2>데이터 샘플 5건</h2><p>delivery_id: " + escape(message.deliveryId())
                + "</p><table border='1'><thead><tr>" + headers
                + "</tr></thead><tbody>" + rows + "</tbody></table>";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
