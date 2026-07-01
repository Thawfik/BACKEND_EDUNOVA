package com.studyplatform.dto.group;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Admin-only: add a registered user to the group directly by their email. */
@Data
public class AddMemberRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;
}