package com.dxdrillbassx.poloniummeme;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Mod(PoloniumMemeMod.MODID)
public class PoloniumMemeMod {
    public static final String MODID = "poloniummeme";

    // Регистрация кастомного звука
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final RegistryObject<SoundEvent> BOX_MUSIC = SOUNDS.register("box_music",
            () -> new SoundEvent(new ResourceLocation(MODID, "box_music")));

    // Хранилище для состояния коробок
    private static final Map<BlockPos, BoxData> activeBoxes = new HashMap<>();

    // Список всех цветов стекла
    private static final List<Block> STAINED_GLASS_COLORS = Arrays.asList(
            Blocks.WHITE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS, Blocks.RED_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS
    );

    private static final Random RANDOM = new Random();

    public PoloniumMemeMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // Инициализация мода
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("spawnbox")
                        .requires(source -> source.hasPermission(2))
                        .executes(this::spawnTemporaryBox)
        );
    }

    private int spawnTemporaryBox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos centerPos = new BlockPos(source.getPosition());

        // Проверка, существует ли уже коробка в этой позиции
        if (activeBoxes.containsKey(centerPos)) {
            source.sendFailure(Component.literal("A box already exists at this location!"));
            return 0;
        }

        // Определяем размеры коробки
        int size = 10;
        BlockState wallState = Blocks.BLACK_CONCRETE.defaultBlockState();
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>();

        // Сохраняем состояние блоков и спавним коробку
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = -size / 2; z <= size / 2; z++) {
                    BlockPos pos = centerPos.offset(x, y, z);
                    if (x == -size / 2 || x == size / 2 || y == 0 || y == size - 1 || z == -size / 2 || z == size / 2) {
                        originalBlocks.put(pos, level.getBlockState(pos));
                        level.setBlock(pos, wallState, 2);
                    }
                }
            }
        }

        // Накладываем эффект слепоты на игроков внутри коробки
        for (Player player : level.getPlayers(p -> p.getBoundingBox().intersects(
                new net.minecraft.world.phys.AABB(centerPos.offset(-size / 2, 0, -size / 2), centerPos.offset(size / 2, size, size / 2))))) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 2, false, false)); // 3.5 секунды, уровень 2
            level.playSound(null, player.getX(), player.getY(), player.getZ(), BOX_MUSIC.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        // Сохраняем данные коробки (16 секунд = 320 тиков, смена пола/потолка через 70 тиков)
        activeBoxes.put(centerPos, new BoxData(originalBlocks, level.getGameTime() + 320, level.getGameTime() + 70, level));

        source.sendSuccess(Component.literal("Box spawned and will despawn in 16 seconds!"), false);
        return 1;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Проверяем все активные коробки
        activeBoxes.entrySet().removeIf(entry -> {
            BlockPos centerPos = entry.getKey();
            BoxData boxData = entry.getValue();
            ServerLevel level = boxData.level;
            long currentTime = level.getGameTime();

            // Смена пола и потолка через 3.5 секунды (70 тиков)
            if (currentTime >= boxData.changeTime && !boxData.floorCeilingChanged) {
                boxData.floorCeilingChanged = true;
                int size = 10;

                // Замена пола и потолка
                for (int x = -size / 2; x <= size / 2; x++) {
                    for (int z = -size / 2; z <= size / 2; z++) {
                        // Пол: светокамень (y=0), стекло (y=1)
                        BlockPos floorPos = centerPos.offset(x, 0, z);
                        BlockPos floorGlassPos = centerPos.offset(x, 1, z);
                        level.setBlock(floorPos, Blocks.GLOWSTONE.defaultBlockState(), 2);
                        level.setBlock(floorGlassPos, getRandomGlassColor(), 2);

                        // Потолок: светокамень (y=9), стекло (y=8)
                        BlockPos ceilingPos = centerPos.offset(x, 9, z);
                        BlockPos ceilingGlassPos = centerPos.offset(x, 8, z);
                        level.setBlock(ceilingPos, Blocks.GLOWSTONE.defaultBlockState(), 2);
                        level.setBlock(ceilingGlassPos, getRandomGlassColor(), 2);
                    }
                }

                // Спавн рыб фугу
                for (int i = 0; i < 200; i++) {
                    double x = centerPos.getX() + (RANDOM.nextDouble() - 0.5) * (size - 2); // Внутренний объём
                    double y = centerPos.getY() + 2 + RANDOM.nextDouble() * (size - 4); // y от 2 до 8
                    double z = centerPos.getZ() + (RANDOM.nextDouble() - 0.5) * (size - 2);
                    Pufferfish pufferfish = new Pufferfish(EntityType.PUFFERFISH, level);
                    pufferfish.setPos(x, y, z);
                    pufferfish.setDeltaMovement(0, 0.3, 0); // Импульс вверх для "прыганья"
                    level.addFreshEntity(pufferfish);
                }
            }

            // Смена цвета стекла каждые 0.2 секунды (4 тика)
            if (boxData.floorCeilingChanged && currentTime % 4 == 0) {
                int size = 10;
                BlockState newGlassColor = getRandomGlassColor();
                for (int x = -size / 2; x <= size / 2; x++) {
                    for (int z = -size / 2; z <= size / 2; z++) {
                        // Пол (y=1)
                        BlockPos floorGlassPos = centerPos.offset(x, 1, z);
                        if (level.getBlockState(floorGlassPos).getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock) {
                            level.setBlock(floorGlassPos, newGlassColor, 2);
                        }
                        // Потолок (y=8)
                        BlockPos ceilingGlassPos = centerPos.offset(x, 8, z);
                        if (level.getBlockState(ceilingGlassPos).getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock) {
                            level.setBlock(ceilingGlassPos, newGlassColor, 2);
                        }
                    }
                }
            }

            // Танец крипера (случайные движения каждые 10 тиков)
            if (boxData.floorCeilingChanged && currentTime % 10 == 0) {
                for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, new net.minecraft.world.phys.AABB(
                                centerPos.offset(-5, 0, -5), centerPos.offset(5, 10, 5)),
                        c -> c.getCustomName() != null && c.getCustomName().getString().equals("PartyCreeper"))) {
                    double moveX = (RANDOM.nextDouble() - 0.5) * 0.2;
                    double moveZ = (RANDOM.nextDouble() - 0.5) * 0.2;
                    creeper.setDeltaMovement(moveX, 0.2, moveZ); // Небольшие "танцевальные" движения
                }
            }

            // Если время истекло, восстанавливаем блоки и останавливаем звук
            if (currentTime >= boxData.despawnTime) {
                for (Map.Entry<BlockPos, BlockState> block : boxData.originalBlocks.entrySet()) {
                    level.setBlock(block.getKey(), block.getValue(), 2);
                }
                // Останавливаем звук для игроков в области
                for (Player player : level.getPlayers(p -> p.getBoundingBox().intersects(
                        new net.minecraft.world.phys.AABB(centerPos.offset(-5, 0, -5), centerPos.offset(5, 10, 5))))) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            new SoundEvent(new ResourceLocation("minecraft", "block.note_block.harp")),
                            SoundSource.BLOCKS, 0.0F, 1.0F); // Остановка звука
                }
                return true; // Удаляем коробку из списка
            }
            return false;
        });
    }

    private BlockState getRandomGlassColor() {
        return STAINED_GLASS_COLORS.get(RANDOM.nextInt(STAINED_GLASS_COLORS.size())).defaultBlockState();
    }

    private static class BoxData {
        final Map<BlockPos, BlockState> originalBlocks;
        final long despawnTime;
        final long changeTime;
        final ServerLevel level;
        boolean floorCeilingChanged;

        BoxData(Map<BlockPos, BlockState> originalBlocks, long despawnTime, long changeTime, ServerLevel level) {
            this.originalBlocks = originalBlocks;
            this.despawnTime = despawnTime;
            this.changeTime = changeTime;
            this.level = level;
            this.floorCeilingChanged = false;
        }
    }
}