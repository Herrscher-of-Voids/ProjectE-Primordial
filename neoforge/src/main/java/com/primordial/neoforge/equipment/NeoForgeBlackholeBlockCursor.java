package com.primordial.neoforge.equipment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
/** 常量内存、由中心向外的平面/立方壳坐标游标，供逐刻采集使用。 */
public final class NeoForgeBlackholeBlockCursor {
    private final int radius;private final Direction.Axis axis;private final boolean volume;
    private int shell=1,face,a=-1,b=-1;private long u,v,du=1,dv,segment=1,progress,turns;private boolean done;
    public NeoForgeBlackholeBlockCursor(int radius,Direction.Axis axis,boolean volume){this.radius=radius;this.axis=axis;this.volume=volume;}
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
