package com.lecturasgas.beta.map;

import android.content.Context;

import org.maplibre.android.MapLibre;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;
import org.maplibre.android.offline.OfflineRegionDefinition;
import org.maplibre.android.offline.OfflineRegionError;
import org.maplibre.android.offline.OfflineRegionStatus;

/**
 * Pegamento entre MapLibre (descarga de mapas sin internet) y Kotlin.
 *
 * Está en Java a propósito: los callbacks de MapLibre son interfaces con parámetros que
 * pueden o no aceptar nulos, y Java no se queja por eso. Así este código compila
 * igual con cualquier versión de la librería. Toda la lógica visible está en OfflineMaps.kt.
 */
public final class OfflineGlue {

    private OfflineGlue() {}

    /** Resultado simple: ok(valor) o fail(mensaje). El valor puede ser null. */
    public interface Callback<T> {
        void ok(T value);

        void fail(String message);
    }

    /** Avisos de una descarga en curso. */
    public interface Progress {
        void status(OfflineRegionStatus status);

        void error();

        void limitExceeded();
    }

    private static OfflineManager manager(Context context) {
        MapLibre.getInstance(context.getApplicationContext());
        return OfflineManager.getInstance(context.getApplicationContext());
    }

    /** Caché general más grande (lo que ves con internet se guarda para después). */
    public static void enlargeCache(Context context, long bytes) {
        manager(context).setMaximumAmbientCacheSize(bytes, new OfflineManager.FileSourceCallback() {
            @Override
            public void onSuccess() {}

            @Override
            public void onError(String message) {}
        });
    }

    public static void setTileLimit(Context context, long limit) {
        manager(context).setOfflineMapboxTileCountLimit(limit);
    }

    /** Busca la zona descargada por esta app (ok(null) si no hay ninguna). */
    public static void findRegion(Context context, final String tag, final Callback<OfflineRegion> cb) {
        manager(context).listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override
            public void onList(OfflineRegion[] regions) {
                OfflineRegion found = null;
                if (regions != null) {
                    for (OfflineRegion r : regions) {
                        byte[] meta = r.getMetadata();
                        if (meta != null && new String(meta).contains(tag)) {
                            found = r;
                            break;
                        }
                    }
                }
                cb.ok(found);
            }

            @Override
            public void onError(String error) {
                cb.fail(error);
            }
        });
    }

    public static void status(OfflineRegion region, final Callback<OfflineRegionStatus> cb) {
        region.getStatus(new OfflineRegion.OfflineRegionStatusCallback() {
            @Override
            public void onStatus(OfflineRegionStatus status) {
                cb.ok(status);
            }

            @Override
            public void onError(String error) {
                cb.fail(error);
            }
        });
    }

    public static void create(Context context, OfflineRegionDefinition definition, byte[] metadata,
                              final Callback<OfflineRegion> cb) {
        manager(context).createOfflineRegion(definition, metadata,
                new OfflineManager.CreateOfflineRegionCallback() {
                    @Override
                    public void onCreate(OfflineRegion region) {
                        cb.ok(region);
                    }

                    @Override
                    public void onError(String error) {
                        cb.fail(error);
                    }
                });
    }

    public static void delete(OfflineRegion region, final Callback<Object> cb) {
        region.delete(new OfflineRegion.OfflineRegionDeleteCallback() {
            @Override
            public void onDelete() {
                cb.ok(null);
            }

            @Override
            public void onError(String error) {
                cb.fail(error);
            }
        });
    }

    public static void observe(OfflineRegion region, final Progress progress) {
        region.setObserver(new OfflineRegion.OfflineRegionObserver() {
            @Override
            public void onStatusChanged(OfflineRegionStatus status) {
                progress.status(status);
            }

            @Override
            public void onError(OfflineRegionError error) {
                progress.error();
            }

            @Override
            public void mapboxTileCountLimitExceeded(long limit) {
                progress.limitExceeded();
            }
        });
    }

    public static void setActive(OfflineRegion region, boolean active) {
        region.setDownloadState(active ? OfflineRegion.STATE_ACTIVE : OfflineRegion.STATE_INACTIVE);
    }
}
