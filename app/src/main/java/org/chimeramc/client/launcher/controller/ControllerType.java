package org.chimeramc.client.launcher.controller;
import android.view.InputDevice;
public enum ControllerType{
    XBOX("Xbox",0x045E,-1,-1),
    DS4("DualShock 4",0x054C,0x05C4,0x09CC),
    DUAL_SENSE("DualSense",0x054C,0x0CE6,-1);

    private final String displayName;
    private final int vendorId;
    private final int productIdA;
    private final int productIdB;

    ControllerType(String displayName,int vendorId,int productIdA,int productIdB){
        this.displayName=displayName;
        this.vendorId=vendorId;
        this.productIdA=productIdA;
        this.productIdB=productIdB;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ControllerType fromVendorProduct(int vendor,int product){
        for(ControllerType t:values()){
            if(t.matches(vendor,product)){
                return t;
            }
        }
        return null;
    }

    public boolean matches(int vendor,int product){
        if(vendor != vendorId){
            return false;
        }
        if(productIdA == -1){
            return true;
        }
        return product==productIdA || (productIdB != -1 && product==productIdB);
    }

    public boolean matches(InputDevice device){
        if(device==null){
            return false;
        }
        int vendor = device.getVendorId();
        int product = device.getProductId();
        return matches(vendor,product);
    }

    public static ControllerType from(InputDevice device){
        if(device==null){
            return null;
        }
        for(ControllerType t:values()){
            if(t.matches(device)){
                return t;
            }
        }
        return null;
    }
}

