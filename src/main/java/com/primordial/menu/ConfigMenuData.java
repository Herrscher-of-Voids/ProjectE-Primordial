/**
 * 【文件职责】将当前机器 FE 和容量通过原版菜单数据同步。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：删除把 32 位能量塞进单个 16 位属性的编码。
 * - 新增：四个属性分别承载当前量及容量的低/高 16 位。
 * - 修改：客户端无符号合并两个分段，支持 0 至 Integer.MAX_VALUE，避免 65535 以上被截断。
 * - 用途：由 ConfiguratorMenu 注册并由 GUI 渲染，不参与 EMC 余额结算。
 */
package com.primordial.menu;

import net.minecraft.world.inventory.ContainerData;

/** 菜单属性只传 16 位；分别拆分当前 FE 和容量，客户端组合为非负 32 位数。 */
public class ConfigMenuData implements ContainerData {
    private final ConfiguratorMenu menu;

    public ConfigMenuData(ConfiguratorMenu menu) {
        this.menu = menu;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> menu.machineEnergyFE & 0xffff;
            case 1 -> menu.machineEnergyFE >>> 16;
            case 2 -> menu.machineEnergyMax & 0xffff;
            case 3 -> menu.machineEnergyMax >>> 16;
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        switch (index) {
            case 0 -> menu.machineEnergyFE = (menu.machineEnergyFE & 0xffff0000) | (value & 0xffff);
            case 1 -> menu.machineEnergyFE = (menu.machineEnergyFE & 0xffff) | ((value & 0x7fff) << 16);
            case 2 -> menu.machineEnergyMax = (menu.machineEnergyMax & 0xffff0000) | (value & 0xffff);
            case 3 -> menu.machineEnergyMax = (menu.machineEnergyMax & 0xffff) | ((value & 0x7fff) << 16);
        }
    }

    @Override
    public int getCount() {
        return 4;
    }
}
