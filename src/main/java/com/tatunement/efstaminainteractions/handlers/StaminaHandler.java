package com.tatunement.efstaminainteractions.handlers;

import com.tatunement.efstaminainteractions.EpicFightStaminaInteractionsMod;
import com.tatunement.efstaminainteractions.config.EpicFightStaminaInteractionsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener;

import java.util.Map;

@Mod.EventBusSubscriber(modid = EpicFightStaminaInteractionsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StaminaHandler {
    private static Map<WeaponCategory, Float> weaponStaminaCosts;

    private static Map<String, Float> animationsStaminaCosts;

    public StaminaHandler() {
        //TODO: Find a way to get the config values for booleans only once (maybe a registry?)
    }

    public static void setWeaponStaminaCosts(Map<WeaponCategory, Float> weaponStaminaCosts) {
        StaminaHandler.weaponStaminaCosts = weaponStaminaCosts;
    }

    public static void setAnimationsStaminaCosts(Map<String, Float> animationsStaminaCosts) {
        StaminaHandler.animationsStaminaCosts = animationsStaminaCosts;
    }

    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player) {
            PlayerPatch<Player> playerPatch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);

            if (playerPatch != null) {
                float currentStamina = playerPatch.getStamina();

                if(player.isSprinting() && !player.isCreative() && EpicFightStaminaInteractionsConfig.enableSprintStamina.get()) {
                    float sprintStaminaCost = EpicFightStaminaInteractionsConfig.SPRINT_STAMINA_COST.get().floatValue();
                    playerPatch.setStamina(Math.max(0.0F, currentStamina - sprintStaminaCost));
                    playerPatch.resetActionTick();
                }

                if(manageJumpingConditions(player)) {
                    if (player.getDeltaMovement().y > 0.05F) { //0.05 because by tests it is the value that has been more consistent with consuming less stamina as possible when going out of water
                        playerPatch.setStamina(Math.max(0.0F, currentStamina - EpicFightStaminaInteractionsConfig.JUMP_STAMINA_COST.get().floatValue()));
                        playerPatch.resetActionTick();
                    }
                }

                if(!player.isCreative() && EpicFightStaminaInteractionsConfig.enableAttackStamina.get()) {
                    Item activeItem = player.getMainHandItem().getItem();
                    if(playerPatch.getStamina() <= 0.0F) {
                        if(activeItem == Items.BOW && player.isUsingItem()) {
                            player.stopUsingItem();
                        } else if (activeItem == Items.CROSSBOW && player.isUsingItem()) {
                            player.stopUsingItem();
                        }
                    } else {
                        if(activeItem == Items.BOW && player.isUsingItem()) {
                            playerPatch.setStamina(Math.max(0.0F, currentStamina - EpicFightStaminaInteractionsConfig.CROSSBOW_STAMINA_COST.get().floatValue()));
                            playerPatch.resetActionTick();
                        } else if (activeItem == Items.CROSSBOW && player.isUsingItem()) {
                            playerPatch.setStamina(Math.max(0.0F, currentStamina - EpicFightStaminaInteractionsConfig.CROSSBOW_STAMINA_COST.get().floatValue()));
                            playerPatch.resetActionTick();
                        }
                    }

                    playerPatch.getEventListener().addEventListener(PlayerEventListener.EventType.BASIC_ATTACK_EVENT, playerPatch.getOriginal().getUUID(), basicAttackEvent -> {
                        if (EpicFightStaminaInteractionsConfig.enableAttackStamina.get() && playerPatch.isEpicFightMode()) {
                            if(playerPatch.getStamina() == 0.0F && event.isCancelable()) {
                                event.setCanceled(true);
                            }
                            CapabilityItem weaponCapability = playerPatch.getHoldingItemCapability(InteractionHand.MAIN_HAND);
                            if (weaponCapability != null) {
                                double weaponDamage = EpicFightStaminaInteractionsConfig.enableDamageScalingCost.get() ? player.getAttribute(Attributes.ATTACK_DAMAGE).getValue() : 0.0D;
                                WeaponCategory weaponCategory = weaponCapability.getWeaponCategory();
                                if(weaponCategory != null) {
                                    float weaponStaminaCost = weaponStaminaCosts.getOrDefault(weaponCategory, 1.0F);
                                    float attackStaminaCost = (float)(weaponDamage * 0.54D + weaponStaminaCost);
                                    float newStamina = Math.max(0.0F, currentStamina - attackStaminaCost);
                                    playerPatch.setStamina(newStamina);
                                }
                            }
                        }
                    });



                    playerPatch.getEventListener().addEventListener(PlayerEventListener.EventType.ANIMATION_BEGIN_EVENT, playerPatch.getOriginal().getUUID(), animationBeginEvent ->  {
                        StaticAnimation animation = animationBeginEvent.getAnimation().getRealAnimation().get();
                        String animationName = animation.getLocation() != null ? animation.getLocation().getPath() : "Cant Load Animation";
                        if(playerPatch.isEpicFightMode() && EpicFightStaminaInteractionsConfig.enableDebugMode.get() && Minecraft.getInstance().isSingleplayer()) {
                            PlayerChatMessage chatMessage = PlayerChatMessage.unsigned(player.getUUID(), "[DEBUG] " + animationName);
                            player.createCommandSourceStack().sendChatMessage(new OutgoingChatMessage.Player(chatMessage), false, ChatType.bind(ChatType.CHAT, player));
                        }
                    });

                    playerPatch.getEventListener().addEventListener(PlayerEventListener.EventType.ANIMATION_END_EVENT, playerPatch.getOriginal().getUUID(), animationEndEvent -> {
                        if(EpicFightStaminaInteractionsConfig.enableAnimationCosts.get()) {
                            StaticAnimation animation = animationEndEvent.getAnimation().getRealAnimation().get();
                            String animationPath = animation.getLocation() != null ? animation.getLocation().getPath() : "";
                            if((!animationsStaminaCosts.isEmpty() || !animationPath.isEmpty()) && animationsStaminaCosts.containsKey(animationPath)) {
                                float animationCost = animationsStaminaCosts.getOrDefault(animationPath, 1.0F);
                                float newStamina = Math.max(0.0F, currentStamina - animationCost);
                                playerPatch.setStamina(newStamina);
                            }
                        }
                    });
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityShieldBlock(ShieldBlockEvent event) {
        if(EpicFightStaminaInteractionsConfig.enableShieldStamina.get()) {
            LivingEntity livingEntity = event.getEntity();
            if (livingEntity instanceof Player player) {
                PlayerPatch<Player> playerPatch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);

                if (playerPatch != null) {
                    float currentStamina = playerPatch.getStamina();
                    if(currentStamina <= 0.0F) {
                        player.getCooldowns().addCooldown(player.getUseItem().getItem(), 80);
                        player.stopUsingItem();
                    } else {
                        float blockedDamage = event.getBlockedDamage();
                        float staminaCost = blockedDamage * EpicFightStaminaInteractionsConfig.SHIELD_STAMINA_MULTIPLIER.get().floatValue();
                        float newStamina = Math.max(0.0F, playerPatch.getStamina() - staminaCost);
                        playerPatch.setStamina(newStamina);
                        playerPatch.resetActionTick();
                    }
                }
            }
        }
    }

    private static boolean manageJumpingConditions(Player player) {
        return EpicFightStaminaInteractionsConfig.enableJumpStamina.get() && (!player.isCreative() && !player.onClimbable() && !player.isSwimming() && !player.isInWater() && !player.isSleeping());
    }
}
