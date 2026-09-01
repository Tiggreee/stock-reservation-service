package lat.vmdev.inventory.support;

public class UnknownSkuException extends RuntimeException {

    private final String sku;

    public UnknownSkuException(String sku) {
        super("no stock record for SKU " + sku);
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
