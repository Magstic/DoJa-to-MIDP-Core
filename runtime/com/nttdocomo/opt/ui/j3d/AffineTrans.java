package com.nttdocomo.opt.ui.j3d;

/**
 *  DoJa 的 3x4 仿射矩陣（Affine Matrix）。
 * 矩陣係數採用 Q12 定點數格式。
 * 確保在 CLDC 下可全程以整數完成運算。 */
public class AffineTrans {
    private static final int ONE = 4096;

    public int m00, m01, m02, m03;
    public int m10, m11, m12, m13;
    public int m20, m21, m22, m23;

    public AffineTrans() { setIdentity(); }

    public AffineTrans(int a00, int a01, int a02, int a03,
            int a10, int a11, int a12, int a13,
            int a20, int a21, int a22, int a23) {
        setElement(a00,a01,a02,a03,a10,a11,a12,a13,a20,a21,a22,a23);
    }

    public void setIdentity() {
        setElement(ONE,0,0,0, 0,ONE,0,0, 0,0,ONE,0);
    }

    public void setElement(int a00, int a01, int a02, int a03,
            int a10, int a11, int a12, int a13,
            int a20, int a21, int a22, int a23) {
        m00=a00; m01=a01; m02=a02; m03=a03;
        m10=a10; m11=a11; m12=a12; m13=a13;
        m20=a20; m21=a21; m22=a22; m23=a23;
    }

    public void setElement(int row, int column, int value) {
        if (row == 0) {
            if (column == 0) m00=value; else if (column == 1) m01=value;
            else if (column == 2) m02=value; else if (column == 3) m03=value;
            else throw new IllegalArgumentException("column");
        } else if (row == 1) {
            if (column == 0) m10=value; else if (column == 1) m11=value;
            else if (column == 2) m12=value; else if (column == 3) m13=value;
            else throw new IllegalArgumentException("column");
        } else if (row == 2) {
            if (column == 0) m20=value; else if (column == 1) m21=value;
            else if (column == 2) m22=value; else if (column == 3) m23=value;
            else throw new IllegalArgumentException("column");
        } else {
            throw new IllegalArgumentException("row");
        }
    }

    public void setRow(int row, int x, int y, int z, int w) {
        if (row == 0) { m00=x; m01=y; m02=z; m03=w; }
        else if (row == 1) { m10=x; m11=y; m12=z; m13=w; }
        else if (row == 2) { m20=x; m21=y; m22=z; m23=w; }
        else throw new IllegalArgumentException("row");
    }

    public void setColumn(int column, int x, int y, int z) {
        if (column == 0) { m00=x; m10=y; m20=z; }
        else if (column == 1) { m01=x; m11=y; m21=z; }
        else if (column == 2) { m02=x; m12=y; m22=z; }
        else if (column == 3) { m03=x; m13=y; m23=z; }
        else throw new IllegalArgumentException("column");
    }

    public void mul(AffineTrans t) {
        if (t == null) throw new NullPointerException("t");
        mul(this, t);
    }

    public void mul(AffineTrans left, AffineTrans right) {
        if (left == null || right == null) throw new NullPointerException();
        int n00=fp3(left.m00,right.m00,left.m01,right.m10,left.m02,right.m20);
        int n01=fp3(left.m00,right.m01,left.m01,right.m11,left.m02,right.m21);
        int n02=fp3(left.m00,right.m02,left.m01,right.m12,left.m02,right.m22);
        int n03=fp3(left.m00,right.m03,left.m01,right.m13,left.m02,right.m23)+left.m03;
        int n10=fp3(left.m10,right.m00,left.m11,right.m10,left.m12,right.m20);
        int n11=fp3(left.m10,right.m01,left.m11,right.m11,left.m12,right.m21);
        int n12=fp3(left.m10,right.m02,left.m11,right.m12,left.m12,right.m22);
        int n13=fp3(left.m10,right.m03,left.m11,right.m13,left.m12,right.m23)+left.m13;
        int n20=fp3(left.m20,right.m00,left.m21,right.m10,left.m22,right.m20);
        int n21=fp3(left.m20,right.m01,left.m21,right.m11,left.m22,right.m21);
        int n22=fp3(left.m20,right.m02,left.m21,right.m12,left.m22,right.m22);
        int n23=fp3(left.m20,right.m03,left.m21,right.m13,left.m22,right.m23)+left.m23;
        setElement(n00,n01,n02,n03,n10,n11,n12,n13,n20,n21,n22,n23);
    }

    public void setRotateX(int angle) {
        int c=fixedCos(angle), s=fixedSin(angle);
        setElement(ONE,0,0,0, 0,c,-s,0, 0,s,c,0);
    }

    public void setRotateY(int angle) {
        int c=fixedCos(angle), s=fixedSin(angle);
        setElement(c,0,s,0, 0,ONE,0,0, -s,0,c,0);
    }

    public void setRotateZ(int angle) {
        int c=fixedCos(angle), s=fixedSin(angle);
        setElement(c,-s,0,0, s,c,0,0, 0,0,ONE,0);
    }

    private static int fp3(int a0,int b0,int a1,int b1,int a2,int b2) {
        return (int)(((long)a0*b0 + (long)a1*b1 + (long)a2*b2) >> 12);
    }
    private static int fixedSin(int angle) {
        return (int)(Math.sin(angle * Math.PI / 2048.0) * ONE);
    }
    private static int fixedCos(int angle) {
        return (int)(Math.cos(angle * Math.PI / 2048.0) * ONE);
    }
}
