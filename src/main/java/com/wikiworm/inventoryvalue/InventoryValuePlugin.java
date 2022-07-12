/*
 *  BSD 2-Clause License
 *
 *  Copyright (c) 2020, wikiworm (Brandon Ripley)
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 *  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 *  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wikiworm.inventoryvalue;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

@PluginDescriptor(name = "Inventory Value")
@Slf4j
public class InventoryValuePlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private InventoryValueConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private InventoryValueOverlay overlay;

    private long _oldInventoryValue         = Long.MIN_VALUE;
    private long _oldProfitInvValue         = Long.MIN_VALUE;
    private long _originalBankValue         = Long.MIN_VALUE;
    private long _lastBankValue             = Long.MIN_VALUE;

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        log.info(String.format("entering: oiv %s opiv %s obv %s lbv %s", _oldInventoryValue, _oldProfitInvValue, _originalBankValue, _lastBankValue));
        long inventoryValue, bankValue, profitInvValue, profitBankValue;
        inventoryValue = profitInvValue = profitBankValue = 0;
        final List<String> ignoredItems = buildIgnoredItemsList();

        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container != null) {
            Item[] items = container.getItems();
            inventoryValue = Arrays.stream(items).flatMapToLong(item ->
                    LongStream.of(calculateItemValue(item, ignoredItems))).sum();
        }
        if(_oldProfitInvValue == Long.MIN_VALUE) profitInvValue = 0;
        else if(event.getContainerId() != InventoryID.BANK.getId()) {
            // subtract the old inventory value from the latest inventory value then and add our existing profit
            profitInvValue = (inventoryValue - _oldInventoryValue) + _oldProfitInvValue;
        }

        // check if we're banking...
        if(event.getContainerId() == InventoryID.BANK.getId()) {
            container = client.getItemContainer(InventoryID.BANK);
            if (container != null) {
                Item[] items = container.getItems();
                bankValue = Arrays.stream(items).flatMapToLong(item ->
                        LongStream.of(calculateItemValue(item, ignoredItems))).sum();

                if(_originalBankValue == Long.MIN_VALUE) _originalBankValue = bankValue;

                profitBankValue = bankValue - _originalBankValue;
                _lastBankValue = bankValue;
            }
        } else {
            profitBankValue = _lastBankValue - _originalBankValue;
        }


        log.info(String.format("displaying: oiv %s pbv %s piv %s ",inventoryValue, profitBankValue, profitInvValue));
        overlay.updateInventoryValue(inventoryValue, profitInvValue, profitBankValue);
        _oldInventoryValue = inventoryValue;
        _oldProfitInvValue = profitInvValue;
        log.info(String.format("exiting: oiv %s opiv %s obv %s lbv %s", _oldInventoryValue, _oldProfitInvValue, _originalBankValue, _lastBankValue));
    }

    public List<String> buildIgnoredItemsList() {
        List<String> ignoredItemsList = Arrays.asList(config.ignoreItems().toLowerCase().split("[,;]"));
        ignoredItemsList.replaceAll(String::trim);
        return ignoredItemsList;
    }

    public long calculateItemValue(Item item, List<String> ignoredItems) {
        int itemId = item.getId();
        if(itemManager != null) {
            ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            String itemName = itemComposition.getName();

            if ((itemId == ItemID.COINS_995 && config.ignoreCoins())) return 0L;
            else if(itemId == ItemID.COINS_995) return item.getQuantity();
            else if (ignoredItems.contains(itemName.toLowerCase())) {
                return 0L;
            }
            long itemValue = (long) item.getQuantity() * (config.useHighAlchemyValue() ?
                    itemComposition.getHaPrice() : itemManager.getItemPrice(itemId));
            return itemValue;
        } else {
            return 0L;
        }
    }

    @Provides
    InventoryValueConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(InventoryValueConfig.class);
    }
}