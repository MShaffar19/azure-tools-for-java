/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azuretools.core.mvp.model;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.appservice.PricingTier;
import com.microsoft.azure.management.resources.Deployment;
import com.microsoft.azure.management.resources.Location;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azuretools.authmanage.AuthMethodManager;
import com.microsoft.azuretools.authmanage.models.SubscriptionDetail;
import com.microsoft.azuretools.sdkmanage.AzureManager;
import com.microsoft.azuretools.utils.AzureModel;
import com.microsoft.azuretools.utils.AzureModelController;
import com.microsoft.azuretools.utils.CanceledByUserException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AzureMvpModel {

    public static final String CANNOT_GET_RESOURCE_GROUP = "Cannot get Resource Group.";
    public static final String APPLICATION_LOG_NOT_ENABLED = "Application log is not enabled.";

    private AzureMvpModel() {
    }

    public static AzureMvpModel getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static String getSegment(String id, String segment) {
        if (StringUtils.isEmpty(id)) {
            return null;
        }
        final String[] attributes = id.split("/");
        int pos = ArrayUtils.indexOf(attributes, segment);
        if (pos >= 0) {
            return attributes[pos + 1];
        }
        return null;
    }

    /**
     * Get subscription by subscriptionId.
     *
     * @param sid Subscription Id
     * @return Instance of Subscription
     */
    @AzureOperation(
        name = "account|subscription.get_detail",
        params = {"$sid"},
        type = AzureOperation.Type.SERVICE
    )
    public Subscription getSubscriptionById(String sid) {
        Subscription ret = null;
        final AzureManager azureManager = AuthMethodManager.getInstance().getAzureManager();
        final Map<String, Subscription> map =
            azureManager.getSubscriptionManager().getSubscriptionIdToSubscriptionMap();
        if (map != null) {
            ret = map.get(sid);
        }
        return ret;
    }

    /**
     * Get list of selected Subscriptions.
     *
     * @return List of Subscription instances
     */
    @AzureOperation(
        name = "account|subscription.get_detail.selected",
        type = AzureOperation.Type.SERVICE
    )
    public List<Subscription> getSelectedSubscriptions() {
        final List<Subscription> ret = new ArrayList<>();
        final AzureManager azureManager = AuthMethodManager.getInstance().getAzureManager();
        if (azureManager == null) {
            return ret;
        }
        Map<String, SubscriptionDetail> sidToSubDetailMap = azureManager.getSubscriptionManager()
                .getSubscriptionIdToSubscriptionDetailsMap();
        Map<String, Subscription> sidToSubscriptionMap = azureManager.getSubscriptionManager()
                .getSubscriptionIdToSubscriptionMap();
        if (sidToSubDetailMap != null && sidToSubscriptionMap != null) {
            for (final SubscriptionDetail subDetail : sidToSubDetailMap.values()) {
                if (subDetail.isSelected()) {
                    ret.add(sidToSubscriptionMap.get(subDetail.getSubscriptionId()));
                }
            }
        }
        Collections.sort(ret, getComparator(Subscription::displayName));
        return ret;
    }

    /**
     * List all the resource groups in selected subscriptions.
     * @return
     */
    @AzureOperation(
        name = "arm|rg.list.subscription|selected",
        type = AzureOperation.Type.SERVICE
    )
    public List<ResourceEx<ResourceGroup>> getResourceGroups(boolean forceUpdate) throws CanceledByUserException {
        List<ResourceEx<ResourceGroup>> resourceGroups = new ArrayList<>();
        Map<SubscriptionDetail, List<ResourceGroup>> srgMap = AzureModel.getInstance()
            .getSubscriptionToResourceGroupMap();
        if (srgMap == null || srgMap.size() < 1 || forceUpdate) {
            AzureModelController.updateSubscriptionMaps(null);
        }
        srgMap = AzureModel.getInstance().getSubscriptionToResourceGroupMap();
        if (srgMap == null) {
            return resourceGroups;
        }
        for (SubscriptionDetail sd : srgMap.keySet()) {
            resourceGroups.addAll(srgMap.get(sd).stream().map(
                resourceGroup -> new ResourceEx<>(resourceGroup, sd.getSubscriptionId())).collect(Collectors.toList()));
        }
        Collections.sort(resourceGroups, getComparator((ResourceEx<ResourceGroup> resourceGroupResourceEx) ->
                resourceGroupResourceEx.getResource().name()));
        return resourceGroups;
    }

    /**
     *
     * @param rgName resource group name
     * @param sid subscription id
     * @return
     */
    @AzureOperation(
        name = "arm|rg.delete",
        params = {"$rgName"},
        type = AzureOperation.Type.SERVICE
    )
    public void deleteResourceGroup(String rgName, String sid) {
        AzureManager azureManager = AuthMethodManager.getInstance().getAzureManager();
        Azure azure = azureManager.getAzure(sid);
        azure.resourceGroups().deleteByName(rgName);
    }

    /**
     * List Resource Group by Subscription ID.
     *
     * @param sid subscription Id
     * @return List of ResourceGroup instances
     */
    @AzureOperation(
        name = "arm|rg.list.subscription",
        params = {"$sid"},
        type = AzureOperation.Type.SERVICE
    )
    public List<ResourceGroup> getResourceGroupsBySubscriptionId(String sid) {
        List<ResourceGroup> ret = new ArrayList<>();
        Azure azure = AuthMethodManager.getInstance().getAzureClient(sid);
        ret.addAll(azure.resourceGroups().list());
        Collections.sort(ret, getComparator(ResourceGroup::name));
        return ret;
    }

    /**
     * Get Resource Group by Subscription ID and Resource Group name.
     */
    @AzureOperation(
        name = "arm|rg.get.subscription",
        params = {"$name", "$sid"},
        type = AzureOperation.Type.SERVICE
    )
    public ResourceGroup getResourceGroupBySubscriptionIdAndName(String sid, String name) throws Exception {
        ResourceGroup resourceGroup;
        Azure azure = AuthMethodManager.getInstance().getAzureClient(sid);
        try {
            resourceGroup = azure.resourceGroups().getByName(name);
            if (resourceGroup == null) {
                throw new Exception(CANNOT_GET_RESOURCE_GROUP);
            }
        } catch (Exception e) {
            throw new Exception(CANNOT_GET_RESOURCE_GROUP);
        }
        return resourceGroup;
    }

    @AzureOperation(
        name = "deployment.list.subscription|selected",
        type = AzureOperation.Type.SERVICE
    )
    public List<Deployment> listAllDeployments() {
        List<Deployment> deployments = new ArrayList<>();
        List<Subscription> subs = getSelectedSubscriptions();
        Observable.from(subs).flatMap((sub) ->
            Observable.create((subscriber) -> {
                List<Deployment> sidDeployments = listDeploymentsBySid(sub.subscriptionId());
                synchronized (deployments) {
                    deployments.addAll(sidDeployments);
                }
                subscriber.onCompleted();
            }).subscribeOn(Schedulers.io()), subs.size()).subscribeOn(Schedulers.io()).toBlocking().subscribe();
        Collections.sort(deployments, getComparator(Deployment::name));
        return deployments;
    }

    @AzureOperation(
        name = "deployment.list.subscription",
        params = {"$sid"},
        type = AzureOperation.Type.SERVICE
    )
    public List<Deployment> listDeploymentsBySid(String sid) {
        Azure azure = AuthMethodManager.getInstance().getAzureClient(sid);
        List<Deployment> deployments = azure.deployments().list();
        Collections.sort(deployments, getComparator(Deployment::name));
        return deployments;
    }

    /**
     * Get deployment by resource group name
     * @param rgName
     * @return
     */
    @AzureOperation(
        name = "deployment.list.subscription|rg",
        params = {"$name", "$sid"},
        type = AzureOperation.Type.SERVICE
    )
    public List<ResourceEx<Deployment>> getDeploymentByRgName(String sid, String rgName) {
        List<ResourceEx<Deployment>> res = new ArrayList<>();
        Azure azure = AuthMethodManager.getInstance().getAzureClient(sid);
        res.addAll(azure.deployments().listByResourceGroup(rgName).stream().
            map(deployment -> new ResourceEx<>(deployment, sid)).collect(Collectors.toList()));
        Collections.sort(res,
                getComparator((ResourceEx<Deployment> deploymentResourceEx) -> deploymentResourceEx.getResource().name()));
        return res;
    }

    /**
     * List Location by Subscription ID.
     *
     * @param sid subscription Id
     * @return List of Location instances
     */
    @AzureOperation(
        name = "common|region.list.subscription",
        params = {"$sid"},
        type = AzureOperation.Type.SERVICE
    )
    public List<Location> listLocationsBySubscriptionId(String sid) {
        List<Location> locations = new ArrayList<>();
        Subscription subscription = getSubscriptionById(sid);
        try {
            locations.addAll(subscription.listLocations());
        } catch (Exception e) {
            e.printStackTrace();
        }
        Collections.sort(locations, getComparator(Location::name));
        return locations;
    }

    /**
     * List all Pricing Tier supported by SDK.
     *
     * @return List of PricingTier instances.
     */
    @AzureOperation(
        name = "common.list_tiers",
        params = {"$name", "$sid"},
        type = AzureOperation.Type.SERVICE
    )
    public List<PricingTier> listPricingTier() {
        final List<PricingTier> ret = new ArrayList<>(PricingTier.getAll());
        ret.sort(getComparator(PricingTier::toString));
        return correctPricingTiers(ret);
    }

    private static <T> Comparator<T> getComparator(Function<T, String> toStringMethod) {
        return (first, second) ->
                StringUtils.compareIgnoreCase(toStringMethod.apply(first), toStringMethod.apply(second));
    }

    // Remove Premium pricing tier which has performance issues with java app services
    private List<PricingTier> correctPricingTiers(final List<PricingTier> pricingTiers) {
        pricingTiers.remove(PricingTier.PREMIUM_P1);
        pricingTiers.remove(PricingTier.PREMIUM_P2);
        pricingTiers.remove(PricingTier.PREMIUM_P3);
        return pricingTiers;
    }

    private static final class SingletonHolder {
        private static final AzureMvpModel INSTANCE = new AzureMvpModel();
    }
}
