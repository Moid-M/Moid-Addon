package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public class ProjectilePredictor extends Module {
    public enum Mode {
        Tick,
        Frame
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> updateMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Frame mode is smoother; Tick mode is more 'classic'.")
        .defaultValue(Mode.Frame)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .build()
    );

    private final Setting<SettingColor> targetColor = sgGeneral.add(new ColorSetting.Builder()
        .name("target-color")
        .description("Color when the path hits an entity.")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build()
    );

    public ProjectilePredictor() {
        super(AddonTemplate.CATEGORY, "proj-predictor", "Smooth frame-based projectile prediction.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        ItemStack stack = mc.player.getMainHandStack();
        if (!isValidItem(stack.getItem())) {
            stack = mc.player.getOffHandStack();
            if (!isValidItem(stack.getItem())) return;
        }

        float delta = (updateMode.get() == Mode.Frame) ? event.tickDelta : 1.0f;
        
        Vec3d pos = getInterpolatedPos(mc.player, delta);
        Vec3d vel = getInitialVelocity(stack.getItem(), delta);

        List<Vec3d> path = new ArrayList<>();
        path.add(pos);

        boolean hitEntity = false;

        for (int i = 0; i < 100; i++) {
            Vec3d nextPos = pos.add(vel);

            BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                pos, nextPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
            ));

            EntityHitResult entityHit = findEntityHit(pos, nextPos);

            if (entityHit != null) {
                path.add(entityHit.getPos());
                event.renderer.box(entityHit.getEntity().getBoundingBox(), targetColor.get(), targetColor.get(), ShapeMode.Both, 0);
                hitEntity = true;
                break;
            } else if (blockHit.getType() != HitResult.Type.MISS) {
                path.add(blockHit.getPos());
                event.renderer.box(blockHit.getBlockPos(), lineColor.get(), lineColor.get(), ShapeMode.Lines, 0);
                break;
            }

            pos = nextPos;
            path.add(pos);

            double drag = getDrag(stack.getItem());
            double gravity = getGravity(stack.getItem());
            vel = vel.multiply(drag).subtract(0, gravity, 0);
        }

        SettingColor finalColor = hitEntity ? targetColor.get() : lineColor.get();
        for (int i = 0; i < path.size() - 1; i++) {
            event.renderer.line(path.get(i).x, path.get(i).y, path.get(i).z, 
                               path.get(i+1).x, path.get(i+1).y, path.get(i+1).z, 
                               finalColor);
        }
    }

    private EntityHitResult findEntityHit(Vec3d start, Vec3d end) {
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity)) continue;
            Box box = entity.getBoundingBox().expand(0.3);
            if (box.raycast(start, end).isPresent()) {
                return new EntityHitResult(entity, box.raycast(start, end).get());
            }
        }
        return null;
    }

    private boolean isValidItem(Item item) {
        return item instanceof BowItem || item instanceof EnderPearlItem || item instanceof EggItem 
            || item instanceof SnowballItem || item instanceof TridentItem || item instanceof CrossbowItem 
            || item instanceof ExperienceBottleItem;
    }

    private Vec3d getInitialVelocity(Item item, float delta) {
        Vec3d look = mc.player.getRotationVec(delta);
        double mult = (item instanceof BowItem || item instanceof CrossbowItem) ? 3.0 : 1.5;
        return look.multiply(mult);
    }

    private double getGravity(Item item) {
        if (item instanceof BowItem || item instanceof CrossbowItem) return 0.05;
        return 0.03;
    }

    private double getDrag(Item item) {
        return 0.99;
    }

    private Vec3d getInterpolatedPos(Entity entity, float delta) {
        // Using lastRender fields to bypass private access on prevX/Y/Z
        double x = entity.lastRenderX + (entity.getX() - entity.lastRenderX) * delta;
        double y = (entity.lastRenderY + (entity.getY() - entity.lastRenderY) * delta) + entity.getEyeHeight(entity.getPose());
        double z = entity.lastRenderZ + (entity.getZ() - entity.lastRenderZ) * delta;
        return new Vec3d(x, y, z);
    }
}