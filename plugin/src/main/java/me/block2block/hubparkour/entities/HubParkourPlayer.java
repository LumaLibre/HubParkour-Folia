package me.block2block.hubparkour.entities;


import com.tcoded.folialib.wrapper.task.WrappedTask;
import me.block2block.hubparkour.HubParkour;
import me.block2block.hubparkour.api.IHubParkourPlayer;
import me.block2block.hubparkour.api.hologram.ILeaderboardHologram;
import me.block2block.hubparkour.api.ParkourRun;
import me.block2block.hubparkour.api.events.player.ParkourPlayerFailEvent;
import me.block2block.hubparkour.api.events.player.ParkourPlayerFinishEvent;
import me.block2block.hubparkour.api.items.*;
import me.block2block.hubparkour.api.plates.Checkpoint;
import me.block2block.hubparkour.managers.CacheManager;
import me.block2block.hubparkour.utils.ConfigUtil;
import me.block2block.hubparkour.utils.TitleUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

@SuppressWarnings("DuplicatedCode")
public class HubParkourPlayer implements IHubParkourPlayer {

    private final Player player;
    private final Parkour parkour;
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private final List<ParkourItem> parkourItems = new ArrayList<>();
    private long currentSplit;
    private Map<Integer, Long> splitTimes;
    private int lastReached = 0;
    private long startTime;
    private long previous = -2;
    private List<Checkpoint> previouslyReachedCheckpoints;
    private boolean lastRunCompleted;
    private ItemStack[] inventory;
    private ItemStack[] extraContents;
    private ItemStack[] armorContents;
    private ItemStack[] storageContents;
    private ItemStack inHand;
    private WrappedTask actionBarTask;
    private GameMode prevGamemode;
    private double prevHealth;
    private double prevMaxHealth;
    private int prevHunger;
    private final ParkourRun parkourRun;
    private boolean touchedGround;

    @SuppressWarnings("unused")
    public HubParkourPlayer(Player p, Parkour parkour) {
        this.parkour = parkour;
        this.player = p;
        parkourRun = new ParkourRun(this);
        startTime = System.currentTimeMillis();
        touchedGround = true;
        currentSplit = startTime;
        HubParkour.getScheduler().runAsync(t -> {
            lastRunCompleted = HubParkour.getInstance().getDbManager().wasCompletedLastRun(p, parkour);
            if ((ConfigUtil.getBoolean("Settings.Repeat-Rewards", true) || ConfigUtil.getBoolean("Settings.Exploit-Prevention.Checkpoint-Rewards-Everytime", false)) && lastRunCompleted) {
                //The last run was completed and repeat rewards are enabled, delete all previously reached checkpoints so they can reach them again.
                previouslyReachedCheckpoints = new ArrayList<>();
                HubParkour.getInstance().getDbManager().resetReachedCheckpoints(p, parkour);
            } else {
                previouslyReachedCheckpoints = HubParkour.getInstance().getDbManager().getReachedCheckpoints(p, parkour);
            }
            previous = HubParkour.getInstance().getDbManager().getTime(p, parkour);
            splitTimes = HubParkour.getInstance().getDbManager().getSplitTimes(p, parkour);
            HubParkour.getInstance().getDbManager().resetLastRun(p, parkour);
        });
        parkourItems.add(new ResetItem(this, ConfigUtil.getInt("Settings.Parkour-Items.Reset.Slot", 5)));
        parkourItems.add(new CheckpointItem(this, ConfigUtil.getInt("Settings.Parkour-Items.Checkpoint.Slot", 4)));
        parkourItems.add(new CancelItem(this, ConfigUtil.getInt("Settings.Parkour-Items.Cancel.Slot", 6)));
        parkourItems.add(new HideItem(this, ConfigUtil.getInt("Settings.Parkour-Items.Hide.Slot", 8)));
    }

    public HubParkourPlayer(HubParkourPlayer p, Parkour parkour) {
        this.player = p.player;
        this.parkour = parkour;
        this.parkourItems.addAll(p.parkourItems);
        parkourItems.forEach(item -> item.setPlayer(this));
        this.splitTimes = new HashMap<>();

        HubParkour.getScheduler().runAsync(t -> {
            lastRunCompleted = HubParkour.getInstance().getDbManager().wasCompletedLastRun(p.player, parkour);
            if ((ConfigUtil.getBoolean("Settings.Repeat-Rewards", true) || ConfigUtil.getBoolean("Settings.Exploit-Prevention.Checkpoint-Rewards-Everytime", false)) && lastRunCompleted) {
                //The last run was completed and repeat rewards are enabled, delete all previously reached checkpoints so they can reach them again.
                previouslyReachedCheckpoints = new ArrayList<>();
                HubParkour.getInstance().getDbManager().resetReachedCheckpoints(p.player, parkour);
            } else {
                previouslyReachedCheckpoints = HubParkour.getInstance().getDbManager().getReachedCheckpoints(p.player, parkour);
            }
            previous = HubParkour.getInstance().getDbManager().getTime(p.player, parkour);
            splitTimes = HubParkour.getInstance().getDbManager().getSplitTimes(p.player, parkour);
            HubParkour.getInstance().getDbManager().resetLastRun(p.player, parkour);
        });

        this.inventory = p.inventory;
        this.extraContents = p.extraContents;
        this.armorContents = p.armorContents;
        this.storageContents = p.storageContents;
        this.prevGamemode = p.prevGamemode;
        this.prevHealth = p.prevHealth;
        this.prevMaxHealth = p.prevMaxHealth;
        this.prevHunger = p.prevHunger;

        if (p.actionBarTask != null) {
            p.actionBarTask.cancel();
        }

        if (ConfigUtil.getBoolean("Settings.Action-Bar.Enabled", true)) {
            actionBarTask = HubParkour.getScheduler().runTimerAsync(() -> {
                String message = HubParkour.c(false, ConfigUtil.getString("Messages.Parkour.Action-Bar", "&a&lCurrent Time: &r{current-time} - &a&lParkour: &r{parkour-name}&r - &a&lCurrent Checkpoint: &r#{current-checkpoint}").replace("{current-time}", ConfigUtil.formatTime((System.currentTimeMillis() - startTime))).replace("{parkour-name}", parkour.getName()).replace("{current-checkpoint}", lastReached + "").replace("{current-splittime}", "" + ((System.currentTimeMillis() - currentSplit)/1000f)));
                if (HubParkour.isPlaceholders()) {
                    message = PlaceholderAPI.setPlaceholders(player, message);
                }
                TitleUtil.sendActionBar(player, message, ChatColor.WHITE, false);
            }, 0, ConfigUtil.getInt("Settings.Action-Bar.Update-Interval", 2));
        }

        parkourRun = new ParkourRun(this);
        startTime = System.currentTimeMillis();
        touchedGround = true;
        currentSplit = startTime;
    }

    public void checkpoint(Checkpoint checkpoint) {
        if (lastReached == checkpoint.getCheckpointNo()) {
            setCurrentSplit();
            return;
        }
        if (!checkpoints.contains(checkpoint)) {
            lastReached = checkpoint.getCheckpointNo();
            parkourRun.checkpointHit();
            long ms = System.currentTimeMillis() - currentSplit;
            Map<String, String> bindings = new HashMap<>();
            bindings.put("checkpoint", checkpoint.getCheckpointNo() + "");
            bindings.put("new-time", ConfigUtil.formatTime(ms));
            if (splitTimes.containsKey(checkpoint.getCheckpointNo())) {
                bindings.put("old-time", ConfigUtil.formatTime(splitTimes.get(checkpoint.getCheckpointNo())));
                if (splitTimes.get(checkpoint.getCheckpointNo()) > ms) {

                    ConfigUtil.sendMessage(player, "Messages.Parkour.Checkpoints.Reached.Beat-Split-Time", "You have reached checkpoint &a#{checkpoint}&r in &a{new-time}s&r and beat your personal best of &a{old-time}s&r!", true, bindings);
                    HubParkour.getScheduler().runAsync(t -> {
                        HubParkour.getInstance().getDbManager().setSplitTime(player, parkour, checkpoint.getCheckpointNo(), ms, true);
                    });
                    splitTimes.put(checkpoint.getCheckpointNo(), ms);
                } else {
                    ConfigUtil.sendMessage(player, "Messages.Parkour.Checkpoints.Reached.Not-Beat-Split-Time", "You have reached checkpoint &a#{checkpoint}&r in &a{new-time}s&r (personal best: {old-time}s)!", true, bindings);
                }
            } else {
                ConfigUtil.sendMessage(player, "Messages.Parkour.Checkpoints.Reached.New-Split-Time", "You have reached checkpoint &a#{checkpoint}&r in &a{new-time}s&r!", true, bindings);
                HubParkour.getScheduler().runAsync(t -> {
                    HubParkour.getInstance().getDbManager().setSplitTime(player, parkour, checkpoint.getCheckpointNo(), ms, false);
                });
                splitTimes.put(checkpoint.getCheckpointNo(), ms);
            }
            checkpoints.add(checkpoint);

            //Give checkpoint reward if not already reached.
            if (!previouslyReachedCheckpoints.contains(checkpoint)) {
                if ((checkpoint.getRewards() != null && !checkpoint.getRewards().isEmpty()) ||
                        (parkour.getGlobalCheckpointCommands() != null && !parkour.getGlobalCheckpointCommands().isEmpty())) {
                    long timestamp = System.currentTimeMillis();
                    if (parkour.getRewardCooldown() != -1) {
                        HubParkour.getScheduler().runAsync(t -> {
                            long timestamp1 = HubParkour.getInstance().getDbManager().getTimestamp(player.getUniqueId(), parkour.getId(), checkpoint.getCheckpointNo());
                            if (timestamp1 != -1) {
                                Calendar calendar = Calendar.getInstance();
                                calendar.setTimeInMillis(timestamp1);
                                calendar.add(Calendar.HOUR_OF_DAY, parkour.getRewardCooldown());
                                if (calendar.getTimeInMillis() > timestamp) {
                                    //They're currently on cooldown, ignore.
                                    return;
                                }
                            }

                            HubParkour.getScheduler().runNextTick(g -> {
                                if (checkpoint.getRewards() != null && !checkpoint.getRewards().isEmpty()) {
                                    for (String command : checkpoint.getRewards()) {
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                                    }
                                }
                                if (parkour.getGlobalCheckpointCommands() != null && !parkour.getGlobalCheckpointCommands().isEmpty()) {
                                    for (String command : parkour.getGlobalCheckpointCommands()) {
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                                    }
                                }
                            });
                            HubParkour.getInstance().getDbManager().updateTimestamp(player.getUniqueId(), parkour.getId(), checkpoint.getCheckpointNo(), timestamp);
                        });
                    } else {
                        HubParkour.getScheduler().runNextTick(t -> {
                            if (checkpoint.getRewards() != null && !checkpoint.getRewards().isEmpty()) {
                                for (String command : checkpoint.getRewards()) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                                }
                            }
                            if (parkour.getGlobalCheckpointCommands() != null && !parkour.getGlobalCheckpointCommands().isEmpty()) {
                                for (String command : parkour.getGlobalCheckpointCommands()) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                                }
                            }
                        });
                    }
                }
            } else {
                if (ConfigUtil.getBoolean("Settings.Exploit-Prevention.Checkpoint-Rewards-Everytime", false)) {
                    HubParkour.getScheduler().runNextTick(t -> {
                        if (checkpoint.getRewards() != null && !checkpoint.getRewards().isEmpty()) {
                            for (String command : checkpoint.getRewards()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                            }
                        }
                        if (parkour.getGlobalCheckpointCommands() != null && !parkour.getGlobalCheckpointCommands().isEmpty()) {
                            for (String command : parkour.getGlobalCheckpointCommands()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                            }
                        }
                    });
                }
            }

            previouslyReachedCheckpoints.add(checkpoint);

            HubParkour.getScheduler().runAsync(t -> {
                HubParkour.getInstance().getDbManager().reachedCheckpoint(player, parkour, checkpoint);
            });
        }
        setCurrentSplit();
    }

    public void end(ParkourPlayerFailEvent.FailCause cause) {
        if (cause != ParkourPlayerFailEvent.FailCause.NEW_PARKOUR) {
            setToPrevState();
        }
        if (cause != null) {
            ParkourPlayerFailEvent failEvent = new ParkourPlayerFailEvent(this.parkour, this, cause);
            Bukkit.getPluginManager().callEvent(failEvent);
            if (failEvent.isCancelled()) {
                return;
            }
            if (actionBarTask != null) {
                actionBarTask.cancel();
                actionBarTask = null;
            }
            switch (cause) {
                case FLY:
                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Failed.Fly", "You are not allowed to fly while doing the parkour. Parkour failed!", true, Collections.emptyMap());
                    break;
                case ELYTRA_USE:
                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Failed.Elytra-Use", "You are not allowed to use an Elytra while doing the parkour. Parkour failed!", true, Collections.emptyMap());
                    break;
                case TELEPORTATION:
                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Failed.Teleportation", "You are not allowed to teleport while doing the parkour. Parkour failed!", true, Collections.emptyMap());
                    break;
                case NEW_PARKOUR:
                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Failed.Parkour-Change", "You have started another parkour, parkour failed!", true, Collections.emptyMap());
                    break;
            }
            HubParkourPlayer pl = this;
            long time = System.currentTimeMillis() - startTime;

            HubParkour.getScheduler().runAsync(t -> {
                HubParkour.getInstance().getDbManager().addAttempt(pl, parkour, time);
            });
        } else {
            if (ConfigUtil.getBoolean("Settings.Must-Complete-All-Checkpoints", true)) {
                if (checkpoints.size() != parkour.getNoCheckpoints()) {
                    ParkourPlayerFailEvent failEvent = new ParkourPlayerFailEvent(this.parkour, this, ParkourPlayerFailEvent.FailCause.NOT_ENOUGH_CHECKPOINTS);
                    Bukkit.getPluginManager().callEvent(failEvent);
                    if (failEvent.isCancelled()) {
                        return;
                    }
                    if (actionBarTask != null) {
                        actionBarTask.cancel();
                        actionBarTask = null;
                    }

                    HubParkourPlayer pl = this;
                    long time = System.currentTimeMillis() - startTime;

                    HubParkour.getScheduler().runAsync(t -> {
                        HubParkour.getInstance().getDbManager().addAttempt(pl, parkour, time);
                    });

                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Failed.Not-Enough-Checkpoints", "You did not reach enough checkpoints, parkour failed!", true, Collections.emptyMap());
                    parkour.playerEnd(this);
                    setToPrevState();
                    CacheManager.playerEnd(this);
                    return;
                }
            }

            long finishMili = System.currentTimeMillis() - startTime;

            long splitMs = System.currentTimeMillis() - currentSplit;

            ParkourPlayerFinishEvent finishEvent = new ParkourPlayerFinishEvent(this.parkour, this, finishMili, finishMili + startTime, startTime);
            Bukkit.getPluginManager().callEvent(finishEvent);
            if (finishEvent.isCancelled()) {
                return;
            }
            if (actionBarTask != null) {
                actionBarTask.cancel();
                actionBarTask = null;
            }


            int check = 0;

            if (!checkpoints.isEmpty()) {
                check = checkpoints.get(checkpoints.size() - 1).getCheckpointNo() + 1;
            }

            HubParkourPlayer pl = this;

            HubParkour.getScheduler().runAsync(t -> {
                HubParkour.getInstance().getDbManager().completedLastRun(player, parkour);
                HubParkour.getInstance().getDbManager().addCompletion(pl, parkour, finishMili);
            });

            Map<String, String> bindings = new HashMap<>();
            bindings.put("new-time", ConfigUtil.formatTime(splitMs));
            if (splitTimes.containsKey(check)) {
                long oldSplit = splitTimes.get(check);
                bindings.put("old-time", ConfigUtil.formatTime(oldSplit));
                if (oldSplit > splitMs) {
                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Split-Time.Beat-Split-Time", "You have reached the finish point in &a{new-time}s&r and beat your personal best of &a{old-time}s&r!", true, bindings);
                    int finalCheck = check;
                    HubParkour.getScheduler().runAsync(t -> {
                        HubParkour.getInstance().getDbManager().setSplitTime(player, parkour, finalCheck, splitMs, true);
                    });
                } else {
                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Split-Time.Not-Beat-Split-Time", "You have reached the finish point in &a{new-time}s&r (personal best: {old-time}s)!", true, bindings);
                }
            } else {
                ConfigUtil.sendMessage(player, "Messages.Parkour.End.Split-Time.New-Split-Time", "You have reached the finish point in &a{new-time}s&r!", true, bindings);
                int finalCheck = check;
                HubParkour.getScheduler().runAsync(t -> {
                    HubParkour.getInstance().getDbManager().setSplitTime(player, parkour, finalCheck, splitMs, false);
                });
            }

            if (previous > 0) {
                if (ConfigUtil.getBoolean("Settings.Repeat-Rewards", true)) {
                    if (parkour.getEndCommands() != null && !parkour.getEndCommands().isEmpty()) {
                        long timestamp = System.currentTimeMillis();
                        if (parkour.getRewardCooldown() != -1) {
                            HubParkour.getScheduler().runAsync(t -> {
                                long timestamp1 = HubParkour.getInstance().getDbManager().getTimestamp(player.getUniqueId(), parkour.getId(), -1);
                                if (timestamp1 != -1) {
                                    Calendar calendar = Calendar.getInstance();
                                    calendar.setTimeInMillis(timestamp1);
                                    calendar.add(Calendar.HOUR_OF_DAY, parkour.getRewardCooldown());
                                    if (calendar.getTimeInMillis() > timestamp) {
                                        //They're currently on cooldown, ignore.
                                        return;
                                    }
                                }
                                HubParkour.getScheduler().runNextTick(g -> {
                                    for (String command : parkour.getEndCommands()) {
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                                    }
                                });
                                HubParkour.getInstance().getDbManager().updateTimestamp(player.getUniqueId(), parkour.getId(), -1, timestamp);
                            });
                        } else {
                            HubParkour.getScheduler().runNextTick(g -> {
                                for (String command : parkour.getEndCommands()) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                                }
                            });
                        }
                    }
                }
                bindings.clear();
                bindings.put("time",ConfigUtil.formatTime(finishMili));
                bindings.put("parkour-name", parkour.getName());
                if (finishMili < previous) {

                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Beat-Previous-Personal-Best", "You beat your previous record and you managed to complete the &a{parkour-name} &rparkour in &a{time} &rseconds!", true, bindings);
                    HubParkour.getScheduler().runAsync(t -> {
                        HubParkour.getInstance().getDbManager().newTime(player, finishMili, true, parkour);
                        int position = HubParkour.getInstance().getDbManager().leaderboardPosition(player, parkour);

                        bindings.clear();
                        bindings.put("position","" + position);
                        bindings.put("suffix",((position % 10 == 1)?"st":((position % 10 == 2)?"nd":((position % 10 == 3)?((position == 13)?"th":"rd"):"th"))));
                        bindings.put("parkour-name",parkour.getName());

                        ConfigUtil.sendMessage(player, "Messages.Parkour.Leaderboard.Leaderboard-Place", "You are in &a{position}{suffix} place&r for the &a{parkour-name}&r parkour!", true, bindings);
                        for (ILeaderboardHologram hologram : parkour.getLeaderboards()) {
                            HubParkour.getScheduler().runAtLocation(hologram.getLocation(), a -> {
                                hologram.refresh();
                            });
                        }
                    });
                } else {
                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Not-Beat-Previous-Personal-Best", "You didn't beat your previous record, but you managed to complete the &a{parkour-name} &rparkour in &a{time} &rseconds!", true, bindings);
                    HubParkour.getScheduler().runAsync(t -> {
                        int position = HubParkour.getInstance().getDbManager().leaderboardPosition(player, parkour);
                        bindings.clear();
                        bindings.put("position","" + position);
                        bindings.put("suffix",((position % 10 == 1)?"st":((position % 10 == 2)?"nd":((position % 10 == 3)?((position == 13)?"th":"rd"):"th"))));
                        bindings.put("parkour-name",parkour.getName());

                        ConfigUtil.sendMessage(player, "Messages.Parkour.Leaderboard.Leaderboard-Place", "You are in &a{position}{suffix} place&r for the &a{parkour-name}&r parkour!", true, bindings);
                    });
                }
            } else {
                if (previous == -1) {
                    if (parkour.getEndCommands() != null && !parkour.getEndCommands().isEmpty()) {
                        long timestamp = System.currentTimeMillis();
                        if (parkour.getRewardCooldown() != -1) {
                            HubParkour.getScheduler().runAsync(t -> {
                                long timestamp1 = HubParkour.getInstance().getDbManager().getTimestamp(player.getUniqueId(), parkour.getId(), -1);
                                if (timestamp1 != -1) {
                                    Calendar calendar = Calendar.getInstance();
                                    calendar.setTimeInMillis(timestamp1);
                                    calendar.add(Calendar.HOUR_OF_DAY, parkour.getRewardCooldown());
                                    if (calendar.getTimeInMillis() > timestamp) {
                                        //They're currently on cooldown, ignore.
                                        return;
                                    }
                                }

                                HubParkour.getScheduler().runNextTick(g -> {
                                    for (String command : parkour.getEndCommands()) {
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                                    }
                                });
                                HubParkour.getInstance().getDbManager().updateTimestamp(player.getUniqueId(), parkour.getId(), -1, timestamp);
                            });
                        } else {
                            HubParkour.getScheduler().runNextTick(g -> {
                                for (String command : parkour.getEndCommands()) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player-name}",player.getName()).replace("{player-uuid}",player.getUniqueId().toString()));
                                }
                            });
                        }

                    }

                    bindings.clear();
                    bindings.put("time",ConfigUtil.formatTime(finishMili));
                    bindings.put("parkour-name", parkour.getName());

                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.First-Time", "Well done! You completed the &a{parkour-name}&r parkour in &a{time}&r seconds! Your reward will be applied shortly!", true, bindings);
                   HubParkour.getScheduler().runAsync(t -> {
                       HubParkour.getInstance().getDbManager().newTime(player, finishMili, false, parkour);
                       int position = HubParkour.getInstance().getDbManager().leaderboardPosition(player, parkour);
                       bindings.clear();
                       bindings.put("position","" + position);
                       bindings.put("suffix",((position % 10 == 1)?"st":((position % 10 == 2)?"nd":((position % 10 == 3)?"rd":"th"))));
                       bindings.put("parkour-name",parkour.getName());

                       ConfigUtil.sendMessage(player, "Messages.Parkour.Leaderboard.Leaderboard-Place", "You are in &a{position}{suffix} place&r for the &a{parkour-name}&r parkour!", true, bindings);
                       for (ILeaderboardHologram hologram : parkour.getLeaderboards()) {
                           HubParkour.getScheduler().runAtLocation(hologram.getLocation(), a -> {
                               hologram.refresh();
                           });
                       }
                   });
                } else {
                    ConfigUtil.sendMessage(player, "Messages.Parkour.End.Failed.Too-Quick", "You completed the parkour too quickly, parkour failed!", true, Collections.emptyMap());
                }
            }
        }

        parkour.playerEnd(this);
        CacheManager.playerEnd(this);

    }

    public int getLastReached() {
        return lastReached;
    }

    public Parkour getParkour() {
        return parkour;
    }

    public Player getPlayer() {
        return player;
    }

    private void setCurrentSplit(){
        currentSplit = System.currentTimeMillis();
    }

    public void restart() {
        startTime = System.currentTimeMillis();
        checkpoints.clear();
        lastReached = 0;
        currentSplit = startTime;
    }

    public long getPrevious() {
        return previous;
    }

    public long getStartTime() {
        return startTime;
    }

    public List<ParkourItem> getParkourItems() {
        return parkourItems;
    }

    @SuppressWarnings("deprecation")
    public void startParkour() {
        inventory = player.getInventory().getContents();
        armorContents = player.getInventory().getArmorContents();
        if (HubParkour.isPost1_8()) {
            extraContents = player.getInventory().getExtraContents();
            storageContents = player.getInventory().getStorageContents();
        }
        inHand = player.getItemOnCursor();
        if (ConfigUtil.getBoolean("Settings.Parkour-Items.Clear-Inventory-On-Parkour-Start", true)) {
            player.getInventory().clear();
            player.setItemOnCursor(null);
        }
        for (ParkourItem item : parkourItems) {
            item.giveItem();
        }

        prevGamemode = player.getGameMode();
        prevHealth = player.getHealth();
        if (HubParkour.isPre1_13()) {
            prevMaxHealth = player.getMaxHealth();
        } else {
            prevMaxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        prevHunger = player.getFoodLevel();
        if (ConfigUtil.getBoolean("Settings.Health.Heal-To-Full", true)) {
            if (prevMaxHealth < 20) {
                if (HubParkour.isPre1_13()) {
                    player.setMaxHealth(20);
                } else {
                    player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20);
                }
            }
            player.setHealth(20);
        }
        if (ConfigUtil.getBoolean("Settings.Hunger.Saturate-To-Full", true)) {
            player.setFoodLevel(30);
        }
        if (ConfigUtil.getBoolean("Settings.Parkour-Gamemode.Enabled", true)) {
            GameMode mode = GameMode.valueOf(ConfigUtil.getString("Settings.Parkour-Gamemode.Gamemode", "ADVENTURE"));
            player.setGameMode(mode);
        }
        if (ConfigUtil.getBoolean("Settings.Action-Bar.Enabled", true)) {
            actionBarTask = HubParkour.getScheduler().runTimerAsync(() -> {
                String message = HubParkour.c(false, ConfigUtil.getString("Messages.Parkour.Action-Bar", "&a&lCurrent Time: &r{current-time} - &a&lParkour: &r{parkour-name}&r - &a&lCurrent Checkpoint: &r#{current-checkpoint}").replace("{current-time}", ConfigUtil.formatTime((System.currentTimeMillis() - startTime))).replace("{parkour-name}", parkour.getName()).replace("{current-checkpoint}", lastReached + "").replace("{current-splittime}", "" + ((System.currentTimeMillis() - currentSplit)/1000f)));
                if (HubParkour.isPlaceholders()) {
                    message = PlaceholderAPI.setPlaceholders(player, message);
                }
                TitleUtil.sendActionBar(player, message, ChatColor.WHITE, false);
            }, 0, ConfigUtil.getInt("Settings.Action-Bar.Update-Interval", 2));
        }
    }

    public WrappedTask getActionBarTask() {
        return actionBarTask;
    }

    public Map<Integer, Long> getSplitTimes() {
        return splitTimes;
    }

    public long getCurrentSplit() {
        return currentSplit;
    }

    public GameMode getPrevGamemode() {
        return prevGamemode;
    }

    @SuppressWarnings("deprecation")
    public void setToPrevState() {
        if (ConfigUtil.getBoolean("Settings.Health.Heal-To-Full", true)) {
            if (HubParkour.isPre1_13()) {
                player.setMaxHealth(prevMaxHealth);
            } else {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(prevMaxHealth);
            }
            player.setHealth(prevHealth);
        }
        if (ConfigUtil.getBoolean("Settings.Parkour-Gamemode.Enabled", true)) {
            player.setGameMode(prevGamemode);
        }
        if (ConfigUtil.getBoolean("Settings.Hunger.Saturate-To-Full", true)) {
            player.setFoodLevel(prevHunger);
        }
        for (ParkourItem item : parkourItems) {
            item.removeItem();
        }
        if (ConfigUtil.getBoolean("Settings.Parkour-Items.Clear-Inventory-On-Parkour-Start", true)) {
            if (inventory != null) {
                player.getInventory().setContents(inventory);
            }
            if (armorContents != null) {
                player.getInventory().setArmorContents(armorContents);
            }
            if (HubParkour.isPost1_8()) {
                if (extraContents != null) {
                    player.getInventory().setExtraContents(extraContents);
                }
                if (storageContents != null) {
                    player.getInventory().setStorageContents(storageContents);
                }
            }
            if (inHand != null) {
                player.getInventory().addItem(inHand);
            }

        }
    }

    public double getPrevHealth() {
        return prevHealth;
    }

    public int getPrevHunger() {
        return prevHunger;
    }

    public ParkourRun getParkourRun() {
        return parkourRun;
    }

    public boolean hasTouchedGround() {
        return touchedGround;
    }

    public void touchedGround() {
        touchedGround = true;
    }

    public void leftGround() {
        touchedGround = false;
    }


}
