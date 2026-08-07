#ifndef KY_MIHOMO_BRIDGE_H
#define KY_MIHOMO_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 启动 Mihomo：config_dir 下须有已清洗的 config.yaml。
 * @return 0 成功；非 0 失败
 */
int ky_mihomo_start(const char *config_dir);

/** 停止 Mihomo（幂等）。 */
void ky_mihomo_stop(void);

/** @return 非 0 表示引擎在跑。 */
int ky_mihomo_is_running(void);

#ifdef __cplusplus
}
#endif

#endif /* KY_MIHOMO_BRIDGE_H */
