package com.zaryvox.headgenerators;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZaryvoxHeadGenerators extends JavaPlugin implements Listener {

    private NamespacedKey headKey;
    private NamespacedKey priceKey;
    private Economy economy;
    private Field activeGensField;
    private long lastReflectionWarning;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        headKey = new NamespacedKey(this, "generator_head");
        priceKey = new NamespacedKey(this, "sell_price");

        setupEconomy();

        try {
            Class<?> generator = Class.forName(
                    "com.github.kleesup.itemgenerators.gen.Generator"
            );

            activeGensField = generator.getDeclaredField("activeGens");
            activeGensField.setAccessible(true);

        } catch (Exception ex) {
            getLogger().warning(
                    "ItemGenerators 1.2 no está disponible al iniciar. " +
                    "Comprueba softdepend y el orden de carga."
            );
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("ZaryvoxHeadGenerators habilitado.");
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (rsp != null) {
            economy = rsp.getProvider();
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onItemSpawn(ItemSpawnEvent event) {

        if (activeGensField == null) return;

        Item entity = event.getEntity();
        ItemStack spawned = entity.getItemStack();

        if (spawned == null || spawned.getType().isAir()) return;

        try {
            Object raw = activeGensField.get(null);

            if (!(raw instanceof Map<?, ?> map)) return;

            for (Object value : map.values()) {

                if (!(value instanceof Collection<?> generators)) continue;

                for (Object generator : generators) {

                    if (!isMatchingGenerator(
                            generator,
                            entity.getLocation(),
                            spawned.getType()
                    )) {
                        continue;
                    }

                    int tier = invokeInt(
                            invoke(generator, "getLevel"),
                            "getNumLevel"
                    );

                    String variant =
                            Math.random() * 100.0 <
                            getConfig().getDouble(
                                    "settings.special-chance-percent",
                                    15.0
                            )
                                    ? "special"
                                    : "normal";

                    ItemStack head = createHead(
                            tier,
                            variant,
                            spawned.getAmount()
                    );

                    if (head != null) {
                        entity.setItemStack(head);
                    }

                    return;
                }
            }

        } catch (Exception ex) {

            long now = System.currentTimeMillis();

            if (now - lastReflectionWarning > 60000) {

                getLogger().warning(
                        "No se pudo identificar el generador: "
                                + ex.getMessage()
                );

                lastReflectionWarning = now;
            }
        }
    }

    private boolean isMatchingGenerator(
            Object generator,
            Location drop,
            Material material
    ) {

        try {
            Object level = invoke(generator, "getLevel");

            ItemStack product =
                    (ItemStack) invoke(level, "getProduct");

            if (product == null || product.getType() != material) {
                return false;
            }

            Location base =
                    (Location) invoke(generator, "getLocation");

            Location expected =
                    base.clone().add(0.5, 1.3, 0.5);

            return expected.getWorld() != null
                    && expected.getWorld().equals(drop.getWorld())
                    && expected.distanceSquared(drop) < 0.04;

        } catch (Exception ignored) {
            return false;
        }
    }

    private Object invoke(
            Object target,
            String method
    ) throws Exception {

        if (target == null) {
            throw new IllegalStateException(
                    "Objeto nulo: " + method
            );
        }

        Method m = target.getClass().getMethod(method);

        return m.invoke(target);
    }

    private int invokeInt(
            Object target,
            String method
    ) throws Exception {

        return ((Number) invoke(target, method)).intValue();
    }

    private ItemStack createHead(
            int tier,
            String variant,
            int amount
    ) {

        String path = "tiers." + tier + "." + variant;

        if (!getConfig().isConfigurationSection(path)) {
            return null;
        }

        ItemStack result = new ItemStack(
                Material.PLAYER_HEAD,
                Math.max(1, Math.min(64, amount))
        );

        SkullMeta meta = (SkullMeta) result.getItemMeta();

        String texture = getConfig()
                .getString(path + ".texture", "")
                .trim();

        if (!texture.isEmpty()) {

            try {
                String textureUrl = extractTextureUrl(texture);

                if (textureUrl == null) {
                    throw new IllegalArgumentException(
                            "Textura inválida. Usa una URL de textures.minecraft.net " +
                            "o el valor Base64 de la propiedad textures."
                    );
                }

                PlayerProfile profile =
                        Bukkit.createPlayerProfile(UUID.randomUUID());

                PlayerTextures textures = profile.getTextures();

                textures.setSkin(new URL(textureUrl));

                profile.setTextures(textures);

                meta.setOwnerProfile(profile);

            } catch (Exception ex) {

                getLogger().warning(
                        "Textura inválida en " + path +
                        ".texture: " + ex.getMessage()
                );
            }
        }

        meta.setDisplayName(
                color(
                        getConfig().getString(
                                path + ".name",
                                "&fCabeza de generador"
                        )
                )
        );

        List<String> lore =
                getConfig().getStringList(path + ".lore");

        if (!lore.isEmpty()) {
            meta.setLore(
                    lore.stream()
                            .map(this::color)
                            .toList()
            );
        }

        double price =
                getConfig().getDouble(
                        path + ".sell-price",
                        0.0
                );

        meta.getPersistentDataContainer().set(
                headKey,
                PersistentDataType.STRING,
                tier + ":" + variant
        );

        meta.getPersistentDataContainer().set(
                priceKey,
                PersistentDataType.DOUBLE,
                price
        );

        result.setItemMeta(meta);

        return result;
    }

    private String extractTextureUrl(String input) {

        String value = input == null ? "" : input.trim();

        if (value.startsWith("https://textures.minecraft.net/texture/")) {
            return value;
        }

        try {
            String decoded = new String(
                    Base64.getDecoder().decode(value),
                    StandardCharsets.UTF_8
            );

            Pattern pattern = Pattern.compile(
                    "https?://textures\\.minecraft\\.net/texture/[a-fA-F0-9]+"
            );

            Matcher matcher = pattern.matcher(decoded);

            if (matcher.find()) {
                return matcher.group();
            }

        } catch (IllegalArgumentException ignored) {
            return null;
        }

        return null;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                text == null ? "" : text
        );
    }

    private String msg(String key) {

        return color(
                getConfig().getString("messages.prefix", "")
                        + getConfig().getString("messages." + key, "")
        );
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (args.length == 1 &&
                args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission(
                    "zaryvoxheadgenerators.admin"
            )) {
                return true;
            }

            reloadConfig();
            setupEconomy();

            sender.sendMessage(msg("reloaded"));

            return true;
        }

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    "Este comando solo puede usarlo un jugador."
            );

            return true;
        }

        if (!player.hasPermission(
                "zaryvoxheadgenerators.sell"
        )) {
            return true;
        }

        if (args.length == 1 &&
                args[0].equalsIgnoreCase("sellhand")) {

            ItemStack hand =
                    player.getInventory().getItemInMainHand();

            double price = getPrice(hand);

            if (price < 0) {
                player.sendMessage(msg("no-head"));
                return true;
            }

            int amount = hand.getAmount();
            double total = amount * price;

            if (!sell(player, total)) {
                return true;
            }

            player.getInventory().setItemInMainHand(null);

            sendSold(player, amount, total);

            return true;
        }

        if (args.length == 1 &&
                args[0].equalsIgnoreCase("sellall")) {

            double total = 0;
            int amount = 0;

            for (int slot = 0;
                 slot < player.getInventory().getSize();
                 slot++) {

                ItemStack item =
                        player.getInventory().getItem(slot);

                double price = getPrice(item);

                if (price >= 0 && item != null) {
                    total += price * item.getAmount();
                    amount += item.getAmount();
                }
            }

            if (amount == 0) {
                player.sendMessage(msg("no-head"));
                return true;
            }

            if (!deposit(player, total)) {
                return true;
            }

            for (int slot = 0;
                 slot < player.getInventory().getSize();
                 slot++) {

                ItemStack item =
                        player.getInventory().getItem(slot);

                if (getPrice(item) >= 0) {
                    player.getInventory().setItem(slot, null);
                }
            }

            sendSold(player, amount, total);

            return true;
        }

        player.sendMessage(msg("invalid"));

        return true;
    }

    private double getPrice(ItemStack item) {

        if (item == null || !item.hasItemMeta()) {
            return -1;
        }

        ItemMeta meta = item.getItemMeta();

        if (!meta.getPersistentDataContainer().has(
                headKey,
                PersistentDataType.STRING
        )) {
            return -1;
        }

        return meta.getPersistentDataContainer().getOrDefault(
                priceKey,
                PersistentDataType.DOUBLE,
                0.0
        );
    }

    private boolean sell(Player player, double total) {
        return deposit(player, total);
    }

    private boolean deposit(
            OfflinePlayer player,
            double total
    ) {

        if (economy == null) {

            if (player.isOnline()) {
                player.getPlayer().sendMessage(msg("no-economy"));
            }

            return false;
        }

        economy.depositPlayer(player, total);

        return true;
    }

    private void sendSold(
            Player player,
            int amount,
            double total
    ) {

        String symbol = getConfig().getString(
                "settings.currency-symbol",
                "$"
        );

        player.sendMessage(
                color(
                        getConfig().getString("messages.prefix", "")
                                + getConfig().getString("messages.sold", "")
                )
                        .replace("%amount%", String.valueOf(amount))
                        .replace(
                                "%money%",
                                symbol + String.format(
                                        Locale.US,
                                        "%.2f",
                                        total
                                )
                        )
        );
    }
}
