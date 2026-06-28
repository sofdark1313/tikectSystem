package com.tikectsystem.enums;

/**
 * @program: 鏋佸害鐪熷疄杩樺師澶ч害缃戦珮骞跺彂瀹炴垬椤圭洰銆?娣诲姞 闃挎槦涓嶆槸绋嬪簭鍛?寰俊锛屾坊鍔犳椂澶囨敞 澶ч害 鏉ヨ幏鍙栭」鐩殑瀹屾暣璧勬枡
 * @description: 鑺傜洰璁㈠崟鏋氫妇
 * @author: 闃挎槦涓嶆槸绋嬪簭鍛?
 **/
public enum ProgramOrderVersion {
    /**
     * 鐗堟湰
     * */
    V1_VERSION("v1","v1鐗堟湰",1),

    V2_VERSION("v2","v2鐗堟湰",2),

    V21_VERSION("v2","v21鐗堟湰",21),

    V3_VERSION("v3","v3鐗堟湰",3),

    V31_VERSION("v3","v31鐗堟湰",31),

    V4_VERSION("v4","v4鐗堟湰",4),

    ;

    private final String version;

    private final String msg;

    private final Integer value;

    ProgramOrderVersion(String version, String msg, Integer value) {
        this.version = version;
        this.msg = msg;
        this.value = value;
    }

    public String getVersion() {
        return version;
    }


    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }

    public Integer getValue(){
        return value;
    }


    public static String getMsg(String version) {
        for (ProgramOrderVersion re : ProgramOrderVersion.values()) {
            if (re.version.equals(version)) {
                return re.msg;
            }
        }
        return "";
    }

    public static ProgramOrderVersion getRc(String version) {
        for (ProgramOrderVersion re : ProgramOrderVersion.values()) {
            if (re.version.equals(version)) {
                return re;
            }
        }
        return null;
    }
}
