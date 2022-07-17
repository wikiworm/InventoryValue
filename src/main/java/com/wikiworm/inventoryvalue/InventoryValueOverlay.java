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

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

@Slf4j
public class InventoryValueOverlay extends Overlay
{
    private Long inventoryValue = 0L;
    private Long startingValue = Long.MIN_VALUE;
    private Long profitInvValue = 0L;
    private Long profitBankValue = 0L;
    private Color profitInvColor = Color.GREEN;
    private Color profitBankColor = Color.GREEN;
    private final InventoryValueConfig inventoryValueConfig;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    private InventoryValueOverlay(InventoryValueConfig config) {
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        this.inventoryValueConfig = config;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        String titleText = "Inventory Value";
        String valueString = inventoryValueConfig.useHighAlchemyValue() ? "HA Price:" : "GE Price:";
        String profitInvString = "Profit (inv):";
        String profitBankString = "Profit (banked):";

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(titleText)
                .color(Color.GREEN)
                .build());

        panelComponent.setPreferredSize(new Dimension(
                graphics.getFontMetrics().stringWidth(titleText) + 30,
                0
        ));

        panelComponent.getChildren().add(LineComponent.builder()
                .left(valueString)
                .leftColor(Color.WHITE)
                .right(QuantityFormatter.quantityToStackSize(inventoryValue))
                .rightColor(Color.YELLOW)
                .build());

        if(inventoryValueConfig.displayProfit()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(profitInvString)
                    .leftColor(Color.WHITE)
                    .right(QuantityFormatter.quantityToStackSize(profitInvValue))
                    .rightColor(profitInvColor)
                    .build());
        }

        if(inventoryValueConfig.displayProfit()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(profitBankString)
                    .leftColor(Color.WHITE)
                    .right(QuantityFormatter.quantityToStackSize(profitBankValue))
                    .rightColor(profitBankColor)
                    .build());
        }

        return panelComponent.render(graphics);
    }


    public void updateInventoryValue(final long newInventoryValue, final long newProfitInvValue, final long newProfitBankValue) {
        final Color updateInvProfitColor = newProfitInvValue >= 0 ? Color.GREEN : Color.RED;
        final Color updateBankProfitColor = newProfitBankValue >= 0 ? Color.GREEN : Color.RED;
        SwingUtilities.invokeLater(() -> inventoryValue = newInventoryValue);
        if(inventoryValueConfig.displayProfit()) {
            SwingUtilities.invokeLater(() -> {
                profitBankValue = newProfitBankValue;
                profitInvValue = newProfitInvValue;
                profitInvColor = updateInvProfitColor;
                profitBankColor = updateBankProfitColor;
            });
        }
    }


}