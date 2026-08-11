package com.aivle.team08.backendspring.email.queue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** 로컬 검증용 SMTP sender. Mailpit을 SES와 같은 EmailDeliverySender 계약으로 감싼다. */
@Component
@ConditionalOnProperty(name = "email.sender", havingValue = "mailpit", matchIfMissing = true)
public class MailpitEmailDeliverySender implements EmailDeliverySender {
    private static final String BRAND_COLOR = "#0f5a52";
    private static final String BRAND_TINT = "#e6f3f3";

    private final JavaMailSender mailSender;
    private final S3Presigner s3Presigner;
    private final String from;
    private final String artifactsBucket;
    private final long artifactsPresignExpiresSeconds;

    public MailpitEmailDeliverySender(
            JavaMailSender mailSender,
            S3Presigner s3Presigner,
            @Value("${email.from:no-reply@local.test}") String from,
            @Value("${s3.artifacts-bucket:}") String artifactsBucket,
            @Value("${s3.artifacts-presign-expires-seconds:259200}") long artifactsPresignExpiresSeconds) {
        this.mailSender = mailSender;
        this.s3Presigner = s3Presigner;
        this.from = from;
        this.artifactsBucket = artifactsBucket;
        this.artifactsPresignExpiresSeconds = artifactsPresignExpiresSeconds;
    }

    @Override
    public SendResult send(EmailDeliveryQueueMessage message) {
        if ("FINAL_ARTIFACT".equals(message.deliveryType())) {
            return sendFinalArtifact(message);
        }
        return sendSample(message);
    }

    private SendResult sendSample(EmailDeliveryQueueMessage message) {
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.recipient());
            helper.setSubject(renderSampleSubject(message));
            helper.setText(renderSampleBody(message), true);
            mailSender.send(mimeMessage);
            return new SendResult("SENT", message.deliveryId(), null);
        } catch (MailException | jakarta.mail.MessagingException exception) {
            return new SendResult("FAILED", null, "SMTP_SEND_FAILED");
        }
    }

    private SendResult sendFinalArtifact(EmailDeliveryQueueMessage message) {
        String storageKey = blankToNull(message.artifactStorageKey());
        if (artifactsBucket.isBlank() || storageKey == null) {
            return new SendResult("FAILED", null, "ARTIFACT_NOT_AVAILABLE");
        }
        String downloadUrl;
        try {
            String filename = blankToNull(message.artifactFilename());
            String mimeType = blankToNull(message.artifactMimeType());
            var getObjectRequest = GetObjectRequest.builder()
                    .bucket(artifactsBucket)
                    .key(storageKey);
            if (filename != null) {
                // 파일명에 한글이 섞이므로 RFC 6266을 따른다: 구형 클라이언트용 ASCII
                // fallback(objet key의 원래 파일명)과, 실제 표시용 filename*(퍼센트
                // 인코딩된 UTF-8)을 같이 둔다. filename*는 인코딩되므로 따옴표·개행 등
                // 헤더 인젝션 문자가 섞여도 안전하다.
                String asciiFallback = storageKey.substring(storageKey.lastIndexOf('/') + 1);
                String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
                getObjectRequest.responseContentDisposition(
                        "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded);
            }
            if (mimeType != null) {
                getObjectRequest.responseContentType(mimeType);
            }
            var presigned = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(artifactsPresignExpiresSeconds))
                    .getObjectRequest(getObjectRequest.build())
                    .build());
            downloadUrl = presigned.url().toString();
        } catch (RuntimeException exception) {
            return new SendResult("FAILED", null, "ARTIFACT_PRESIGN_FAILED");
        }

        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.recipient());
            helper.setSubject(renderFinalArtifactSubject(message));
            helper.setText(renderFinalArtifactBody(message, downloadUrl), true);
            mailSender.send(mimeMessage);
            return new SendResult("SENT", message.deliveryId(), null);
        } catch (MailException | jakarta.mail.MessagingException exception) {
            return new SendResult("FAILED", null, "SMTP_SEND_FAILED");
        }
    }

    private String renderSampleSubject(EmailDeliveryQueueMessage message) {
        String title = blankToNull(message.requestTitle());
        return title == null
                ? "[하나 데이터 사업부] 샘플 데이터 안내"
                : "[하나 데이터 사업부] 샘플 데이터 안내 - " + title;
    }

    private String renderFinalArtifactSubject(EmailDeliveryQueueMessage message) {
        String title = blankToNull(message.requestTitle());
        return title == null
                ? "[하나 데이터 사업부] 최종 산출물 안내"
                : "[하나 데이터 사업부] 최종 산출물 안내 - " + title;
    }

    private String renderSampleBody(EmailDeliveryQueueMessage message) {
        String greeting = greeting(message);
        String contactLine = contactLine(message, "샘플 내용이 요청 의도와 다르거나 승인·반려 관련 문의가 있으시면");

        String tableHeaders = message.sampleColumns().stream()
                .map(column -> escape(String.valueOf(column.getOrDefault("name", "column"))))
                .map(name -> "<th style=\"padding:8px 10px;background:" + BRAND_TINT + ";color:" + BRAND_COLOR
                        + ";font-size:12px;text-align:left;border-bottom:1px solid #d5e6e3;\">" + name + "</th>")
                .collect(Collectors.joining());
        String tableRows = message.sampleRows().stream()
                .map(row -> row.values().stream()
                        .map(value -> "<td style=\"padding:8px 10px;font-size:12px;color:#495057;border-bottom:1px solid #e9ecef;\">"
                                + escape(String.valueOf(value)) + "</td>")
                        .collect(Collectors.joining("", "<tr>", "</tr>")))
                .collect(Collectors.joining());

        String content = "<p style=\"margin:0 0 16px;font-size:15px;color:#1a1a1b;line-height:1.6;\">" + greeting
                + "<br/>요청하신 데이터의 샘플 5건을 안내드립니다. 검토용 자료로, 실제 산출물과 형식·값이 다를 수 있습니다.</p>"
                + infoTable(message)
                + "<h3 style=\"margin:0 0 12px;font-size:14px;color:" + BRAND_COLOR + ";\">데이터 샘플 5건</h3>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">"
                + "<thead><tr>" + tableHeaders + "</tr></thead><tbody>" + tableRows + "</tbody></table>"
                + "<p style=\"margin:24px 0 0;font-size:13px;color:#495057;line-height:1.6;\">" + contactLine + "</p>";

        return wrapEmailShell(content);
    }

    private String renderFinalArtifactBody(EmailDeliveryQueueMessage message, String downloadUrl) {
        String greeting = greeting(message);
        String contactLine = contactLine(message, "최종 산출물 내용이 요청 의도와 다르거나 승인·반려 관련 문의가 있으시면");
        String expiresDays = String.valueOf(Math.max(1, artifactsPresignExpiresSeconds / 86400));
        String filename = blankToNull(message.artifactFilename());

        String content = "<p style=\"margin:0 0 16px;font-size:15px;color:#1a1a1b;line-height:1.6;\">" + greeting
                + "<br/>요청하신 데이터의 최종 산출물이 준비되었습니다. 아래 버튼으로 다운로드해 주세요.</p>"
                + infoTable(message)
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 24px;\">"
                + "<tr><td style=\"border-radius:6px;background:" + BRAND_COLOR + ";\">"
                + "<a href=\"" + escape(downloadUrl) + "\" style=\"display:inline-block;padding:12px 24px;font-size:14px;"
                + "font-weight:700;color:#ffffff;text-decoration:none;\">최종 산출물 다운로드</a>"
                + "</td></tr></table>"
                + "<p style=\"margin:0 0 24px;font-size:12px;color:#adb5bd;\">"
                + (filename != null ? "파일명: " + escape(filename) + "<br/>" : "")
                + "이 링크는 " + expiresDays + "일간만 유효합니다. 만료되면 담당자에게 재발송을 요청해 주세요.</p>"
                + apiCredentials(message)
                + "<p style=\"margin:0;font-size:13px;color:#495057;line-height:1.6;\">" + contactLine + "</p>";

        return wrapEmailShell(content);
    }

    private String greeting(EmailDeliveryQueueMessage message) {
        String companyName = blankToNull(message.clientCompanyName());
        return companyName != null
                ? escape(companyName) + " 담당자님, 하나 데이터 사업부입니다."
                : "안녕하세요, 하나 데이터 사업부입니다.";
    }

    private String contactLine(EmailDeliveryQueueMessage message, String prefix) {
        String ownerName = blankToNull(message.ownerName());
        String ownerEmail = blankToNull(message.ownerEmail());
        return (ownerName != null && ownerEmail != null)
                ? prefix + " 담당자 " + escape(ownerName) + "(<a href=\"mailto:" + escape(ownerEmail)
                        + "\" style=\"color:" + BRAND_COLOR + ";\">" + escape(ownerEmail) + "</a>)에게 회신해 주세요."
                : prefix + " 본 메일에 회신해 주세요.";
    }

    private String infoTable(EmailDeliveryQueueMessage message) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 24px;font-size:13px;\">"
                + infoRow("요청번호", message.requestNo())
                + infoRow("요청 제목", message.requestTitle())
                + infoRow("고객사", message.clientCompanyName())
                + infoRow("담당자", message.ownerName())
                + "</table>";
    }

    private String wrapEmailShell(String content) {
        return "<div style=\"font-family:'Malgun Gothic','Apple SD Gothic Neo',sans-serif;background:#f4f6f5;padding:24px 0;margin:0;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;margin:0 auto;background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e0e5e3;\">"
                + "<tr><td style=\"background:" + BRAND_COLOR + ";padding:24px 32px;\">"
                + "<span style=\"color:#ffffff;font-size:18px;font-weight:700;\">하나 데이터 사업부</span>"
                + "</td></tr>"
                + "<tr><td style=\"padding:32px;\">" + content + "</td></tr>"
                + "<tr><td style=\"background:#f8f9fa;padding:16px 32px;border-top:1px solid #e9ecef;\">"
                + "<p style=\"margin:0;font-size:11px;color:#adb5bd;\">본 메일은 발신 전용입니다. &copy; 포트폴리오 데모 데이터 사업부</p>"
                + "</td></tr>"
                + "</table>"
                + "</div>";
    }

    private String infoRow(String label, String value) {
        String display = blankToNull(value) != null ? escape(value) : "-";
        return "<tr>"
                + "<td style=\"padding:4px 0;width:96px;color:#adb5bd;\">" + escape(label) + "</td>"
                + "<td style=\"padding:4px 0;color:#495057;\">" + display + "</td>"
                + "</tr>";
    }

    private String apiCredentials(EmailDeliveryQueueMessage message) {
        if (blankToNull(message.apiEndpointUrl()) == null || blankToNull(message.apiKey()) == null) {
            return "";
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 24px;border:1px solid #d5e6e3;border-radius:6px;background:"
                + BRAND_TINT + ";\">"
                + "<tr><td colspan=\"2\" style=\"padding:16px 16px 8px;font-weight:700;color:" + BRAND_COLOR + ";\">API 접속 정보</td></tr>"
                + apiInfoRow("API URL", message.apiEndpointUrl())
                + apiInfoRow("API Key", message.apiKey())
                + "</table>";
    }

    private String apiInfoRow(String label, String value) {
        return "<tr>"
                + "<td style=\"padding:4px 16px;width:96px;vertical-align:top;color:#7c9692;\">" + escape(label) + "</td>"
                + "<td style=\"padding:4px 16px;vertical-align:top;color:#285e58;word-break:break-all;overflow-wrap:anywhere;\">"
                + escape(value) + "</td></tr>";
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
