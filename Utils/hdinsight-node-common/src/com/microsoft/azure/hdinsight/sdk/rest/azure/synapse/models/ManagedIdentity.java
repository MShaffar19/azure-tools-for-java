/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.hdinsight.sdk.rest.azure.synapse.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The workspace managed identity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ManagedIdentity {
    /**
     * The principal ID of the workspace managed identity.
     */
    @JsonProperty(value = "principalId", access = JsonProperty.Access.WRITE_ONLY)
    private String principalId;

    /**
     * The tenant ID of the workspace managed identity.
     */
    @JsonProperty(value = "tenantId", access = JsonProperty.Access.WRITE_ONLY)
    private String tenantId;

    /**
     * The type of managed identity for the workspace. Possible values include: 'None', 'SystemAssigned'.
     */
    @JsonProperty(value = "type")
    private ResourceIdentityType type;

    /**
     * Get the principal ID of the workspace managed identity.
     *
     * @return the principalId value
     */
    public String principalId() {
        return this.principalId;
    }

    /**
     * Get the tenant ID of the workspace managed identity.
     *
     * @return the tenantId value
     */
    public String tenantId() {
        return this.tenantId;
    }

    /**
     * Get the type of managed identity for the workspace. Possible values include: 'None', 'SystemAssigned'.
     *
     * @return the type value
     */
    public ResourceIdentityType type() {
        return this.type;
    }

    /**
     * Set the type of managed identity for the workspace. Possible values include: 'None', 'SystemAssigned'.
     *
     * @param type the type value to set
     * @return the ManagedIdentity object itself.
     */
    public ManagedIdentity withType(ResourceIdentityType type) {
        this.type = type;
        return this;
    }

}
