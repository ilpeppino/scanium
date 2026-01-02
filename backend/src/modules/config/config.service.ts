import fs from 'fs/promises';
import path from 'path';
import { Config } from '../../config/index.js';

export interface RemoteConfig {
  version: string;
  fetchedAt?: number;
  ttlSeconds: number;
  featureFlags: Record<string, boolean>;
  limits: Record<string, number>;
  experiments: Record<string, any>;
  rollouts?: Record<string, number>;
}

export class ConfigService {
  private configPath: string;
  private currentConfig: RemoteConfig | null = null;
  private lastLoaded: number = 0;

  constructor(private appConfig: Config) {
    this.configPath = path.resolve(process.cwd(), 'config', 'remote-config.json');
    // Mark appConfig as used (reserved for future configuration needs)
    void this.appConfig;
  }

  async getConfig(deviceHash?: string, _region?: string): Promise<RemoteConfig> {
    await this.ensureConfigLoaded();
    
    if (!this.currentConfig) {
        throw new Error("Failed to load config");
    }

    const baseConfig = structuredClone(this.currentConfig);
    
    // Add server timestamp
    baseConfig.fetchedAt = Date.now();

    // 1. Apply rollouts
    if (baseConfig.rollouts && deviceHash) {
        const hashNum = this.hashToNumber(deviceHash);
        for (const [feature, percentage] of Object.entries(baseConfig.rollouts)) {
            // Only apply rollout if flag is not already explicitly enabled
            if (!baseConfig.featureFlags[feature]) {
                if (percentage > 0 && (hashNum % 100) < percentage) {
                    baseConfig.featureFlags[feature] = true;
                }
            }
        }
    }
    
    // 2. Remove internal fields
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { rollouts, ...clientConfig } = baseConfig;
    return clientConfig as RemoteConfig;
  }

  private async ensureConfigLoaded() {
    // Reload every minute if needed
    if (Date.now() - this.lastLoaded > 60000 || !this.currentConfig) {
        try {
            const data = await fs.readFile(this.configPath, 'utf-8');
            this.currentConfig = JSON.parse(data);
            this.lastLoaded = Date.now();
        } catch (e) {
            console.error(`Error loading remote config from ${this.configPath}`, e);
            if (!this.currentConfig) {
                // Fallback safe default
                this.currentConfig = {
                    version: "0.0.0",
                    ttlSeconds: 3600,
                    featureFlags: {},
                    limits: {},
                    experiments: {},
                    rollouts: {}
                };
            }
        }
    }
  }

  private hashToNumber(hash: string): number {
      let sum = 0;
      for (let i = 0; i < hash.length; i++) {
          sum += hash.charCodeAt(i);
      }
      return sum;
  }
}
