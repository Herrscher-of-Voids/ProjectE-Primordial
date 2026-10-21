/**
 * 【变更记录 #1】2026-10-14 02:34:14（第 1 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：立方体按六面去重外壳遍历，锤/钉头锤体积采掘不会分配整个范围；平面由中心向外展开。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.equipment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
/** 常量内存、由中心向外的平面/立方壳坐标游标，供逐刻采集使用。 */
public final class BlackholeBlockCursor {
    private final int radius;private final Direction.Axis axis;private final boolean volume;
    private int shell=1,face,a=-1,b=-1;private long u,v,du=1,dv,segment=1,progress,turns;private boolean done;
    public BlackholeBlockCursor(int radius,Direction.Axis axis,boolean volume){this.radius=radius;this.axis=axis;this.volume=volume;}
    public BlockPos next(){
        if(volume)return cube();if(done||radius<0)return null;
        BlockPos result=axis==Direction.Axis.X?new BlockPos(0,(int)u,(int)v):axis==Direction.Axis.Y?new BlockPos((int)u,0,(int)v):new BlockPos((int)u,(int)v,0);
        u+=du;v+=dv;if(++progress==segment){progress=0;long old=du;du=-dv;dv=old;if(++turns%2==0)segment++;}
        if(Math.max(Math.abs(u),Math.abs(v))>radius)done=true;return result;
    }
    private BlockPos cube(){
        if(shell>radius)return null;
        BlockPos result=switch(face){case 0->new BlockPos(-shell,a,b);case 1->new BlockPos(shell,a,b);case 2->new BlockPos(a,-shell,b);case 3->new BlockPos(a,shell,b);case 4->new BlockPos(a,b,-shell);default->new BlockPos(a,b,shell);};
        int maxA=face<2?shell:shell-1,maxB=face<4?shell:shell-1;
        if(++a>maxA){a=face<2?-shell:-shell+1;if(++b>maxB){if(++face>5){face=0;shell++;}a=face<2?-shell:-shell+1;b=face<4?-shell:-shell+1;}}
        return result;
    }
}
