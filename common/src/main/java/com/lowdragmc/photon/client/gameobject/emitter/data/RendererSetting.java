package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.BooleanConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorSelectorConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * @author KilaBash
 * @date 2023/6/4
 * @implNote RendererSetting
 */
@Environment(EnvType.CLIENT)
@Getter
@Setter
public class RendererSetting {

    public enum Layer {
        Opaque,
        Translucent
    }

    @Configurable(tips = "photon.emitter.config.renderer.layer")
    protected Layer layer = Layer.Translucent;

    @Configurable(tips = "photon.emitter.config.renderer.bloomEffect")
    protected boolean bloomEffect = false;

    @Configurable(name = "cull", subConfigurable = true, tips = "photon.emitter.config.renderer.cull")
    protected final Cull cull = new Cull();

    public static class Cull extends ToggleGroup {
        @Setter
        @Getter
        @Configurable
        @NumberRange(range = {-10000, 10000})
        protected Vector3f from = new Vector3f(-0.5f, -0.5f, -0.5f);

        @Setter
        @Getter
        @Configurable
        @NumberRange(range = {-10000, 10000})
        protected Vector3f to = new Vector3f(0.5f, 0.5f, 0.5f);

        public AABB getCullAABB(Emitter particle, float partialTicks) {
            var pos = particle.transform().position();
            return new AABB(from.x, from.y, from.z, to.x, to.y, to.z).move(pos.x, pos.y, pos.z);
        }
    }

    @Getter
    @Setter
    public static class Particle extends RendererSetting implements IConfigurable, IPersistedSerializable {

        public enum Mode {
            Billboard((p, c, t) -> c.rotation()),
            Horizontal(0, 90),
            Vertical(0, 0),
            VerticalBillboard((p, c, t) -> {
                var quaternion = new Quaternionf();
                quaternion.rotateY((float) Math.toRadians(-c.getYRot()));
                return quaternion;
            }),
            LookAtDirection((p, c, t) -> getParticleLookAtDirection(p, p.getRealVelocity().normalize(), c)),
            Beam((p, c, t) -> {
                Quaternionf rotation = p.getEmitter().transform().rotation();

                Vector3f yAxis = new Vector3f(0, 1, 0); // 初始向上的向量
                rotation.transform(yAxis); // 旋转后的 yAxis
                return getParticleLookAtDirection(p, yAxis, c);
            }),
            Model((p, c, t) -> new Quaternionf());

            public final TriFunction<TileParticle, Camera, Float, Quaternionf> quaternion;

            Mode(TriFunction<TileParticle, Camera, Float, Quaternionf> quaternion) {
                this.quaternion = quaternion;
            }

            Mode(Quaternionf quaternion) {
                this.quaternion = (p, c, t) -> quaternion;
            }

            Mode(float yRot, float xRot) {
                var quaternion = new Quaternionf();
                quaternion.rotateY((float) Math.toRadians(-yRot));
                quaternion.rotateX((float) Math.toRadians(xRot));
                this.quaternion = (p, c, t) -> quaternion;
            }

            private static Quaternionf getParticleLookAtDirection(TileParticle particle, Vector3f yAxis, Camera camera) {
                // 获取相机到粒子的方向（粒子到相机方向的负方向）
                Vector3f cameraToParticle = new Vector3f(particle.getLocalPos()).sub(camera.getPosition().toVector3f()).normalize();

                // 计算X轴：Y轴和相机方向的叉积
                Vector3f xAxis = new Vector3f();
                yAxis.cross(cameraToParticle, xAxis).normalize();

                // 如果Y轴和相机方向几乎平行，使用默认朝向
                if (xAxis.length() < 1e-6f) {
                    return new Quaternionf(); // 返回单位四元数
                }

                // 重新计算Z轴：X轴和Y轴的叉积（确保正交）
                Vector3f zAxis = new Vector3f();
                xAxis.cross(yAxis, zAxis).normalize();

                // 从这三个轴构建旋转矩阵，然后转换为四元数
                Matrix3f rotationMatrix = new Matrix3f();
                rotationMatrix.setColumn(0, xAxis);
                rotationMatrix.setColumn(1, yAxis);
                rotationMatrix.setColumn(2, zAxis);

                return new Quaternionf().setFromNormalized(rotationMatrix);
            }
        }

        @Persisted
        protected Mode renderMode = Mode.Billboard;
        @Nullable
        protected IModelRenderer model;
        @Persisted
        protected boolean shade = true;
        @Persisted
        protected boolean useBlockUV = true;

        @Override
        public void buildConfigurator(ConfiguratorGroup father) {
            var configurator = new ConfiguratorSelectorConfigurator<>("renderMode",
                    false, this::getRenderMode, this::setRenderMode, Mode.Billboard, true,
                    Arrays.stream(Mode.values()).toList(), Mode::name, (mode, container) -> {
                if (mode == Mode.Model) {
                    if (model == null) {
                        model = new IModelRenderer(new ResourceLocation("block/dirt"));
                    }
                    model.buildConfigurator(container);
                    var shadeConfigurator = new BooleanConfigurator("shade", this::isShade, this::setShade, true, true);
                    shadeConfigurator.setTips("photon.emitter.config.renderer.renderMode.model.shade");
                    container.addConfigurators(shadeConfigurator);
                    var useBlockUVConfigurator = new BooleanConfigurator("useBlockUV", this::isUseBlockUV, this::setUseBlockUV, true, true);
                    shadeConfigurator.setTips("photon.emitter.config.renderer.renderMode.model.useBlockUV");
                    container.addConfigurators(useBlockUVConfigurator);

                }
            });
            configurator.setTips("photon.emitter.config.renderer.renderMode");
            father.addConfigurators(configurator);
            IConfigurable.super.buildConfigurator(father);
        }

        public IModelRenderer getModel() {
            if (model == null) {
                model = new IModelRenderer(new ResourceLocation("block/dirt"));
            }
            return model;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            IPersistedSerializable.super.deserializeNBT(tag);
            if (renderMode == Mode.Model) {
                if (model == null) {
                    model = new IModelRenderer(new ResourceLocation("block/dirt"));
                }
                model.deserializeNBT(tag.getCompound("model"));
            }
        }

        @Override
        public CompoundTag serializeNBT() {
            var tag = IPersistedSerializable.super.serializeNBT();
            if (renderMode == Mode.Model && model != null) {
                tag.put("model", getModel().serializeNBT());
            }
            return tag;
        }
    }
}
