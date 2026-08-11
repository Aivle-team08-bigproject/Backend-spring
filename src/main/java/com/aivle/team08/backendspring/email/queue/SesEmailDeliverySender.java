package com.aivle.team08.backendspring.email.queue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/** 운영용 Amazon SES sender. Mailpit과 동일한 무상태 sender 계약을 사용한다. */
@Component
@ConditionalOnProperty(name = "email.sender", havingValue = "ses")
public class SesEmailDeliverySender implements EmailDeliverySender {
    private final SesV2Client ses;
    private final S3Presigner s3Presigner;
    private final String from;
    private final String artifactsBucket;
    private final long artifactsPresignExpiresSeconds;

    public SesEmailDeliverySender(
            SesV2Client ses,
            S3Presigner s3Presigner,
            @Value("${email.from}") String from,
            @Value("${s3.artifacts-bucket:}") String artifactsBucket,
            @Value("${s3.artifacts-presign-expires-seconds:259200}") long artifactsPresignExpiresSeconds) {
        this.ses = ses;
        this.s3Presigner = s3Presigner;
        this.from = from;
        this.artifactsBucket = artifactsBucket;
        this.artifactsPresignExpiresSeconds = artifactsPresignExpiresSeconds;
    }

    @Override
    public SendResult send(EmailDeliveryQueueMessage message) {
        try {
            String subject;
            String html;
            if ("FINAL_ARTIFACT".equals(message.deliveryType())) {
                String url = presignArtifact(message);
                subject = subject(message, "최종 산출물 안내");
                html = finalArtifactHtml(message, url);
            } else {
                if (message.sampleRows() == null || message.sampleRows().size() != 5) {
                    return new SendResult("FAILED", null, "SAMPLE_PAYLOAD_INVALID");
                }
                subject = subject(message, "샘플 데이터 안내");
                html = sampleHtml(message);
            }

            SendEmailRequest request = SendEmailRequest.builder()
                    .fromEmailAddress(from)
                    .destination(Destination.builder().toAddresses(message.recipient()).build())
                    .content(EmailContent.builder().simple(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder().html(Content.builder().data(html).charset("UTF-8").build()).build())
                            .build()).build())
                    .build();
            String messageId = ses.sendEmail(request).messageId();
            return new SendResult("SENT", messageId, null);
        } catch (SdkException | IllegalArgumentException exception) {
            return new SendResult("FAILED", null, "SES_SEND_FAILED");
        }
    }

    private String presignArtifact(EmailDeliveryQueueMessage message) {
        if (artifactsBucket.isBlank() || message.artifactStorageKey() == null
                || message.artifactStorageKey().isBlank()) {
            throw new IllegalArgumentException("artifact is not available");
        }
        GetObjectRequest.Builder request = GetObjectRequest.builder()
                .bucket(artifactsBucket)
                .key(message.artifactStorageKey());
        if (message.artifactFilename() != null && !message.artifactFilename().isBlank()) {
            String fallback = message.artifactStorageKey()
                    .substring(message.artifactStorageKey().lastIndexOf('/') + 1);
            String encoded = URLEncoder.encode(message.artifactFilename(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            request.responseContentDisposition(
                    "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded);
        }
        if (message.artifactMimeType() != null && !message.artifactMimeType().isBlank()) {
            request.responseContentType(message.artifactMimeType());
        }
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(artifactsPresignExpiresSeconds))
                .getObjectRequest(request.build())
                .build()).url().toString();
    }

    private String subject(EmailDeliveryQueueMessage message, String fallback) {
        return "[하나 데이터 사업부] " + fallback
                + (message.requestTitle() == null || message.requestTitle().isBlank()
                        ? "" : " - " + escape(message.requestTitle()));
    }

    private String sampleHtml(EmailDeliveryQueueMessage message) {
        StringBuilder rows = new StringBuilder();
        for (var row : message.sampleRows()) {
            rows.append("<tr>");
            for (Object value : row.values()) {
                rows.append("<td style=\"padding:8px;border-bottom:1px solid #eee\">")
                        .append(escape(String.valueOf(value))).append("</td>");
            }
            rows.append("</tr>");
        }
        return shell("<p>요청하신 데이터의 샘플 5건을 안내드립니다.</p>"
                + info(message) + "<table><tbody>" + rows + "</tbody></table>");
    }

    private String finalArtifactHtml(EmailDeliveryQueueMessage message, String url) {
        String filename = escape(message.artifactFilename() == null ? "result.csv" : message.artifactFilename());
        return shell("<p>요청하신 최종 산출물이 완료되었습니다.</p>" + info(message)
                + "<p><a href=\"" + escape(url) + "\">" + filename + " 다운로드</a></p>"
                + apiCredentials(message)
                + "<p>다운로드 링크는 설정된 만료 시간 이후 사용할 수 없습니다.</p>");
    }

    private String info(EmailDeliveryQueueMessage message) {
        return "<p>요청번호: " + escape(message.requestNo()) + "<br/>"
                + "고객사: " + escape(message.clientCompanyName()) + "<br/>"
                + "담당자: " + escape(message.ownerName()) + "</p>";
    }

    private String apiCredentials(EmailDeliveryQueueMessage message) {
        if (message.apiEndpointUrl() == null || message.apiEndpointUrl().isBlank()
                || message.apiKey() == null || message.apiKey().isBlank()) {
            return "";
        }
        return "<h3>API 접속 정보</h3><p>API URL: <span style=\"word-break:break-all;overflow-wrap:anywhere;\">"
                + escape(message.apiEndpointUrl()) + "</span>"
                + "<br/>API Key: <span style=\"word-break:break-all;overflow-wrap:anywhere;\">"
                + escape(message.apiKey()) + "</span></p>";
    }

    private String shell(String content) {
        return "<html><body style=\"font-family:Arial,sans-serif;color:#1a1a1b\">"
                + content + "</body></html>";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
