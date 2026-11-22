package com.bizpilot.email.dto;

public record EmailRequest(String to, String subject, String htmlBody) {}
