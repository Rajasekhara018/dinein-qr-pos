package com.heuristiq.uam.application.client.persistence.jpa;

import java.util.Set;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.uam.enums.UamEnums.ApiClientType;
import com.heuristiq.uam.enums.UamEnums.ClientAuthenticationMethod;
import com.heuristiq.uam.enums.UamEnums.GrantType;
import com.heuristiq.uam.helper.AuditInfo;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_clients", uniqueConstraints = @UniqueConstraint(name = "uk_client_id", columnNames = "client_id"))
public class ApiClientEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String tenantId;

    private String applicationId;

    @NotBlank
    @Column(name = "client_id", unique = true)
    private String clientId;

    private String clientSecretHash;

    @NotBlank
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    private ApiClientType clientType;

    @Enumerated(EnumType.STRING)
    private ClientAuthenticationMethod authenticationMethod;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_client_grant_types", joinColumns = @JoinColumn(name = "api_client_id"))
    @Column(name = "grant_type")
    @Enumerated(EnumType.STRING)
    private Set<GrantType> grantTypes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_client_scopes", joinColumns = @JoinColumn(name = "api_client_id"))
    @Column(name = "scope")
    private Set<String> scopes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_client_role_ids", joinColumns = @JoinColumn(name = "api_client_id"))
    @Column(name = "role_id")
    private Set<String> roleIds;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_client_allowed_ips", joinColumns = @JoinColumn(name = "api_client_id"))
    @Column(name = "ip_address")
    private Set<String> allowedIpAddresses;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_client_redirect_uris", joinColumns = @JoinColumn(name = "api_client_id"))
    @Column(name = "redirect_uri")
    private Set<String> redirectUris;

    private Integer accessTokenValiditySeconds;

    private Integer refreshTokenValiditySeconds;

    @Builder.Default
    private Boolean active = true;

    @Embedded
    private AuditInfo audit;
}
