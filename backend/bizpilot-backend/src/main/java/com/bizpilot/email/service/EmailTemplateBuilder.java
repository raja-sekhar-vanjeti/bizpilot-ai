package com.bizpilot.email.service;

import com.bizpilot.email.dto.EmailRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailTemplateBuilder {

    private static final String NAVY = "#0f1f3d";
    private static final String BLUE = "#2563eb";

    public EmailRequest buildPaymentReminder(
            String to,
            String customerName,
            String invoiceNumber,
            BigDecimal invoiceAmount,
            BigDecimal outstandingAmount,
            String currency,
            LocalDate dueDate,
            String businessName) {

        String safeCustomer = HtmlUtils.htmlEscape(customerName);
        String safeInvoice = HtmlUtils.htmlEscape(invoiceNumber);
        String safeCurrency = HtmlUtils.htmlEscape(currency);
        String safeBusiness = HtmlUtils.htmlEscape(businessName);
        String invoiceAmt = formatAmount(invoiceAmount);
        String outstandingAmt = formatAmount(outstandingAmount);
        String due = dueDate.toString();

        String subject = "Payment Reminder \u2013 Invoice " + invoiceNumber;

        String htmlBody = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Payment Reminder</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f6fa;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f6fa;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:8px;overflow:hidden;">
                          <tr>
                            <td style="background-color:%1$s;padding:20px 24px;">
                              <h1 style="margin:0;font-size:22px;line-height:28px;color:#ffffff;">Payment Reminder</h1>
                              <div style="height:4px;width:48px;background-color:%2$s;margin-top:8px;"></div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px;font-size:15px;line-height:22px;">
                              <p style="margin:0 0 16px 0;">Dear %3$s,</p>
                              <p style="margin:0 0 16px 0;">We hope you are doing well.</p>
                              <p style="margin:0 0 16px 0;">This is a friendly reminder regarding the outstanding payment for the following invoice:</p>
                              <table role="presentation" width="100%%" cellpadding="8" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:6px;margin:0 0 16px 0;">
                                <tr><td style="font-weight:bold;color:%1$s;">Invoice Number:</td><td>%4$s</td></tr>
                                <tr><td style="font-weight:bold;color:%1$s;">Invoice Amount:</td><td>%5$s %6$s</td></tr>
                                <tr><td style="font-weight:bold;color:%1$s;">Amount Outstanding:</td><td style="color:%2$s;font-weight:bold;">%5$s %7$s</td></tr>
                                <tr><td style="font-weight:bold;color:%1$s;">Due Date:</td><td>%8$s</td></tr>
                              </table>
                              <p style="margin:0 0 16px 0;">We kindly request you to arrange the pending payment at your earliest convenience.</p>
                              <p style="margin:0 0 16px 0;">If you have already completed the payment, please disregard this reminder.</p>
                              <p style="margin:0 0 16px 0;">Thank you for your business.</p>
                              <p style="margin:0;">Best regards,<br><strong style="color:%1$s;">%9$s</strong></p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(NAVY, BLUE, safeCustomer, safeInvoice, safeCurrency,
                invoiceAmt, outstandingAmt, due, safeBusiness);

        return new EmailRequest(to, subject, htmlBody);
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
