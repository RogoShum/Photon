package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib.client.scene.ParticleManager;
import com.lowdragmc.lowdraglib.gui.editor.ui.sceneeditor.SceneEditorWidget;
import com.lowdragmc.lowdraglib.gui.editor.ui.sceneeditor.sceneobject.ISceneInteractable;
import com.lowdragmc.lowdraglib.gui.editor.ui.sceneeditor.sceneobject.ISceneObject;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.photon.client.PhotonParticleManager;
import lombok.Getter;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Iterator;

/**
 * @author KilaBash
 * @date 2023/5/26
 * @implNote ParticleScene
 */
@Getter
public class ParticleScene extends SceneEditorWidget {
    protected final PhotonParticleManager particleManager = new PhotonParticleManager();

    public ParticleScene(int x, int y, int width, int height) {
        super(x, y, width, height, null);
    }

    @Override
    protected ParticleManager createParticleManager() {
        return particleManager;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.intractable) {
            if (this.isCameraMoving) {
                if (this.renderer != null) {
                    // 计算位移方向（基于摄像机视角）
                    Vec3 lookVec = new Vec3(new Vector3f(this.renderer.getLookAt()).sub(this.renderer.getEyePos()));  // 前向向量
                    Vec3 upVec = new Vec3(0, 1, 0);        // 世界向上向量
                    Vec3 rightVec = lookVec.cross(upVec).normalize();  // 右向向量（与 lookVec 和 upVec 垂直）
                    Vec3 moveVec = Vec3.ZERO;

                    // 根据按键组合计算位移
                    moveVec = moveVec.add(upVec.scale(dragY));
                    moveVec = moveVec.add(rightVec.scale(-dragX));

                    // 归一化并缩放位移（避免斜向移动速度更快）
                    if (moveVec.lengthSqr() > 0) {
                        moveVec = moveVec.scale(0.05);  // 0.2 是移动速度，可调整
                    }

                    // 更新摄像机（如果需要）
                    if (this.renderer != null) {
                        this.center.add(moveVec.toVector3f());
                        this.renderer.setCameraLookAt(this.center, (double)this.camZoom(), Math.toRadians((double)this.rotationPitch), Math.toRadians((double)this.rotationYaw));
                    }
                }

                return false;
            }
        }

        if (this.dragging) {
            this.rotationPitch += (float) dragX;
            this.rotationPitch %= 360.0F;
            this.rotationYaw = (float) Mth.clamp((double) this.rotationYaw + dragY, -89.9, 89.9);
            if (this.renderer != null) {
                this.renderer.setCameraLookAt(this.center, (double) this.camZoom(), Math.toRadians((double) this.rotationPitch), Math.toRadians((double) this.rotationYaw));
            }

            return false;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 先调用父类逻辑（如 UI 输入处理）
        boolean handled = super.charTyped(codePoint, modifiers);
        if (handled) {
            return true;
        }

        // 获取 Minecraft 实例和摄像机
        Minecraft mc = Minecraft.getInstance();

        // 获取玩家绑定的移动按键（WASD 或方向键）
        KeyMapping forwardKey = mc.options.keyUp;
        KeyMapping backKey = mc.options.keyDown;
        KeyMapping leftKey = mc.options.keyLeft;
        KeyMapping rightKey = mc.options.keyRight;

        // 检查按下的键是否匹配移动键
        boolean isForward = isKeyPressed(codePoint, forwardKey);
        boolean isBack = isKeyPressed(codePoint, backKey);
        boolean isLeft = isKeyPressed(codePoint, leftKey);
        boolean isRight = isKeyPressed(codePoint, rightKey);

        // 如果没有按下移动键，直接返回
        if (!isForward && !isBack && !isLeft && !isRight) {
            return false;
        }

        // 计算位移方向（基于摄像机视角）
        Vec3 lookVec = new Vec3(new Vector3f(this.renderer.getLookAt()).sub(this.renderer.getEyePos()));  // 前向向量
        Vec3 upVec = new Vec3(0, 1, 0);        // 世界向上向量
        Vec3 rightVec = lookVec.cross(upVec).normalize();  // 右向向量（与 lookVec 和 upVec 垂直）
        Vec3 moveVec = Vec3.ZERO;

        // 根据按键组合计算位移
        if (isForward) moveVec = moveVec.add(lookVec);
        if (isBack) moveVec = moveVec.subtract(lookVec);
        if (isLeft) moveVec = moveVec.subtract(rightVec);
        if (isRight) moveVec = moveVec.add(rightVec);

        // 归一化并缩放位移（避免斜向移动速度更快）
        if (moveVec.lengthSqr() > 0) {
            moveVec = moveVec.normalize().scale(this.moveSpeed);  // 0.2 是移动速度，可调整
        }

        // 更新摄像机（如果需要）
        if (this.renderer != null) {
            this.center.add(moveVec.toVector3f());
            this.renderer.setCameraLookAt(this.center, (double)this.camZoom(), Math.toRadians((double)this.rotationPitch), Math.toRadians((double)this.rotationYaw));
        }

        return true;
    }

    /**
     * 检查输入的字符是否匹配按键绑定的默认键
     */
    private boolean isKeyPressed(char codePoint, KeyMapping keyMapping) {
        // 获取绑定的按键名称（如 "key.keyboard.w"）
        String keyName = keyMapping.getDefaultKey().getName();
        // 转换为小写字母（如 "w"）
        char keyChar = keyName.toLowerCase().charAt(keyName.length() - 1);
        return Character.toLowerCase(codePoint) == keyChar;
    }

    public boolean checkMouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        for(int i = this.widgets.size() - 1; i >= 0; --i) {
            Widget widget = (Widget)this.widgets.get(i);
            if (widget.isVisible() && widget.isActive() && widget.mouseWheelMove(mouseX, mouseY, wheelDelta)) {
                return true;
            }
        }

        return false;
    }
}
