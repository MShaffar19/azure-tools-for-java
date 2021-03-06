/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.hdinsight.sdk.cluster;

import com.microsoft.azuretools.azurecommons.helpers.NotNull;
import rx.Observable;

public interface DestroyableCluster {
    @NotNull
    Observable<? extends DestroyableCluster> destroy();
}
