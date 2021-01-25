/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.springcloud.runner.deploy;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.management.appplatform.v2020_07_01.implementation.AppPlatformManager;
import com.microsoft.azure.management.appplatform.v2020_07_01.implementation.AppResourceInner;
import com.microsoft.azure.management.appplatform.v2020_07_01.implementation.DeploymentResourceInner;
import com.microsoft.azure.toolkit.intellij.common.AzureRunProfileState;
import com.microsoft.azure.toolkit.intellij.springcloud.SpringCloudUtils;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.springcloud.*;
import com.microsoft.azure.toolkit.lib.springcloud.config.SpringCloudDeploymentConfig;
import com.microsoft.azure.toolkit.lib.springcloud.model.ScaleSettings;
import com.microsoft.azure.tools.utils.RxUtils;
import com.microsoft.azuretools.authmanage.AuthMethodManager;
import com.microsoft.azuretools.telemetry.TelemetryConstants;
import com.microsoft.azuretools.telemetrywrapper.Operation;
import com.microsoft.azuretools.telemetrywrapper.TelemetryManager;
import com.microsoft.intellij.RunProcessHandler;
import com.microsoft.tooling.msservices.serviceexplorer.azure.springcloud.SpringCloudStateManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.microsoft.azure.toolkit.lib.springcloud.AzureSpringCloudConfigUtils.DEFAULT_DEPLOYMENT_NAME;

@Slf4j
public class SpringCloudDeploymentState extends AzureRunProfileState<AppResourceInner> {
    private static final int GET_URL_TIMEOUT = 60;
    private static final int GET_STATUS_TIMEOUT = 180;
    private static final String UPDATE_APP_WARNING = "It may take some moments for the configuration to be applied at server side!";
    private static final String GET_DEPLOYMENT_STATUS_TIMEOUT = "Deployment succeeded but the app is still starting, " +
        "you can check the app status from Azure Portal.";

    private final SpringCloudDeployConfiguration config;

    public SpringCloudDeploymentState(Project project, SpringCloudDeployConfiguration configuration) {
        super(project);
        this.config = configuration;
    }

    @Nullable
    @Override
    public AppResourceInner executeSteps(@NotNull RunProcessHandler processHandler, @NotNull Map<String, String> telemetryMap) throws Exception {
        // prepare the jar to be deployed
        updateTelemetryMap(telemetryMap);
        final File artifact = SpringCloudUtils.getArtifact(config.getArtifactIdentifier(), project);
        final boolean enableDisk = config.getDeployment() != null && config.getDeployment().isEnablePersistentStorage();
        final String clusterName = config.getClusterName();
        final String clusterId = config.getClusterId();
        final String appName = config.getAppName();

        final SpringCloudDeploymentConfig deploymentConfig = config.getDeployment();
        final Map<String, String> env = deploymentConfig.getEnvironment();
        final String jvmOptions = deploymentConfig.getJvmOptions();
        final ScaleSettings scaleSettings = deploymentConfig.getScaleSettings();
        final String runtimeVersion = deploymentConfig.getJavaVersion();

        final AzureSpringCloud az = AzureSpringCloud.az(this.getAppPlatformManager());
        final SpringCloudCluster cluster = az.cluster(clusterName);
        final SpringCloudApp app = cluster.app(appName);
        final String deploymentName = StringUtils.firstNonBlank(app.getActiveDeploymentName(), DEFAULT_DEPLOYMENT_NAME);
        final SpringCloudDeployment deployment = app.deployment(deploymentName);

        final boolean toCreateApp = !app.exists();
        final boolean toCreateDeployment = !deployment.exists();

        final List<AzureTask<?>> tasks = new ArrayList<>();
        if (toCreateApp) {
            log.info("Creating app({})...", appName);
            app.create().commit();
            SpringCloudStateManager.INSTANCE.notifySpringAppUpdate(clusterId, getInner(app.entity()), null);
            log.info("Successfully created the app.");
        }
        log.info("Uploading artifact({}) to Azure...", artifact.getPath());
        final SpringCloudApp.Uploader artifactUploader = app.uploadArtifact(artifact.getPath());
        artifactUploader.commit();
        log.info("Successfully uploaded the artifact.");

        final SpringCloudDeployment.Updater deploymentModifier = (toCreateDeployment ? deployment.create() : deployment.update())
            .configEnvironmentVariables(env)
            .configJvmOptions(jvmOptions)
            .configScaleSettings(scaleSettings)
            .configRuntimeVersion(runtimeVersion)
            .configArtifact(artifactUploader.getArtifact());
        log.info(toCreateDeployment ? "Creating deployment({})..." : "Updating deployment({})...", deploymentName);
        deploymentModifier.commit();
        log.info(toCreateDeployment ? "Successfully created the deployment" : "Successfully updated the deployment");
        SpringCloudStateManager.INSTANCE.notifySpringAppUpdate(clusterId, getInner(app.entity()), getInner(deployment.entity()));

        final SpringCloudApp.Updater appUpdater = app.update()
            .activate(StringUtils.firstNonBlank(app.getActiveDeploymentName(), deploymentName))
            .setPublic(config.isPublic())
            .enablePersistentDisk(enableDisk);
        if (!appUpdater.isSkippable()) {
            log.info("Updating app({})...", appName);
            appUpdater.commit();
            log.info("Successfully updated the app.");
            log.warn(UPDATE_APP_WARNING);
        }

        SpringCloudStateManager.INSTANCE.notifySpringAppUpdate(clusterId, getInner(app.entity()), getInner(deployment.entity()));
        if (!deployment.waitUntilReady(GET_STATUS_TIMEOUT)) {
            log.warn(GET_DEPLOYMENT_STATUS_TIMEOUT);
        }
        printPublicUrl(app);
        return getInner(app.entity());
    }

    @Override
    protected Operation createOperation() {
        return TelemetryManager.createOperation(TelemetryConstants.SPRING_CLOUD, TelemetryConstants.CREATE_SPRING_CLOUD_APP);
    }

    @Override
    protected void onSuccess(AppResourceInner result, @NotNull RunProcessHandler processHandler) {
        setText(processHandler, "Deploy succeed");
        processHandler.notifyComplete();
    }

    @Override
    protected String getDeployTarget() {
        return "SPRING_CLOUD";
    }

    @Override
    protected void updateTelemetryMap(@NotNull Map<String, String> telemetryMap) {
        telemetryMap.putAll(config.getModel().getTelemetryProperties());
    }

    private void printPublicUrl(final SpringCloudApp app) {
        if (!app.entity().isPublic()) {
            return;
        }
        log.info("Getting public url of app({})...", app.name());
        String publicUrl = app.entity().getApplicationUrl();
        if (StringUtils.isEmpty(publicUrl)) {
            publicUrl = RxUtils.pollUntil(() -> app.refresh().entity().getApplicationUrl(), StringUtils::isNotBlank, GET_URL_TIMEOUT);
        }
        if (StringUtils.isEmpty(publicUrl)) {
            log.warn("Failed to get application url");
        } else {
            log.info("Application url: {}", publicUrl);
        }
    }

    @SneakyThrows
    private static AppResourceInner getInner(final SpringCloudAppEntity app) {
        final Field inner = SpringCloudAppEntity.class.getDeclaredField("inner");
        inner.setAccessible(true);
        return (AppResourceInner) inner.get(app);
    }

    @SneakyThrows
    private static DeploymentResourceInner getInner(final SpringCloudDeploymentEntity deployment) {
        final Field inner = SpringCloudDeploymentEntity.class.getDeclaredField("inner");
        inner.setAccessible(true);
        return (DeploymentResourceInner) inner.get(deployment);
    }

    private AppPlatformManager getAppPlatformManager() {
        return AuthMethodManager.getInstance().getAzureSpringCloudClient(this.config.getSubscriptionId());
    }
}
