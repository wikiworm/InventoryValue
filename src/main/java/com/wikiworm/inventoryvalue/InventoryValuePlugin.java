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
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
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

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == InventoryID.INVENTORY.getId())
        {
            long inventoryValue;
            List<String> ignoredItems = buildIgnoredItemsList();

            ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
            if (container != null)
            {
                Item[] items = container.getItems();
                inventoryValue = Arrays.stream(items).parallel().flatMapToLong(item ->
                        LongStream.of(calculateItemValue(item, ignoredItems))).sum();

                overlay.updateInventoryValue(inventoryValue);
            }
        }
    }

    public List<String> buildIgnoredItemsList()
    {
        List<String> ignoredItemsList = Arrays.asList(config.ignoreItems().toLowerCase().split("[,;]"));
        ignoredItemsList.replaceAll(String::trim);
        return ignoredItemsList;
    }

    public long calculateItemValue(Item item, List<String> ignoredItems)
    {
        int itemId = item.getId();
        if(itemManager != null) {
            ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            String itemName = itemComposition.getName();

            if ((itemId == ItemID.COINS_995 && config.ignoreCoins()) || ignoredItems.contains(itemName.toLowerCase()))
            {
                return 0L;
            }
            return (long) item.getQuantity() * (config.useHighAlchemyValue() ?
                    itemComposition.getHaPrice() : itemManager.getItemPrice(item.getId()));
        } else {
            return 0L;
        }

    }

    @Provides
    InventoryValueConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(InventoryValueConfig.class);
    }
}