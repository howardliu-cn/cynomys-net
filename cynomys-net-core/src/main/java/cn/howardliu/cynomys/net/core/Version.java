package cn.howardliu.cynomys.net.core;

/**
 * 版本信息
 *
 * @author Howard Liu <howardliu1988@163.com>
 * Created on 2021-06-01
 */
public final class Version {
    private Version() {
    }

    public static final int CURRENT_VERSION = VersionEnum.V0_0_1.getVersion();
    public static final String CURRENT_VERSION_DESC = VersionEnum.V0_0_1.name();

    public enum VersionEnum {
        V0_0_1(0x0000_0_0_1);

        private final int version;

        VersionEnum(int version) {
            this.version = version;
        }

        public int getVersion() {
            return version;
        }
    }
}
