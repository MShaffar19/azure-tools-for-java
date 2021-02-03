/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.hdinsight.sdk.rest.azure.synapse.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Spark pool auto-pausing properties.
 * Auto-pausing properties of a Big Data pool powered by Apache Spark.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoPauseProperties {
    /**
     * Number of minutes of idle time before the Big Data pool is automatically paused.
     */
    @JsonProperty(value = "delayInMinutes")
    private Integer delayInMinutes;

    /**
     * Whether auto-pausing is enabled for the Big Data pool.
     */
    @JsonProperty(value = "enabled")
    private Boolean enabled;

    /**
     * Get number of minutes of idle time before the Big Data pool is automatically paused.
     *
     * @return the delayInMinutes value
     */
    public Integer delayInMinutes() {
        return this.delayInMinutes;
    }

    /**
     * Set number of minutes of idle time before the Big Data pool is automatically paused.
     *
     * @param delayInMinutes the delayInMinutes value to set
     * @return the AutoPauseProperties object itself.
     */
    public AutoPauseProperties withDelayInMinutes(Integer delayInMinutes) {
        this.delayInMinutes = delayInMinutes;
        return this;
    }

    /**
     * Get whether auto-pausing is enabled for the Big Data pool.
     *
     * @return the enabled value
     */
    public Boolean enabled() {
        return this.enabled;
    }

    /**
     * Set whether auto-pausing is enabled for the Big Data pool.
     *
     * @param enabled the enabled value to set
     * @return the AutoPauseProperties object itself.
     */
    public AutoPauseProperties withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

}
