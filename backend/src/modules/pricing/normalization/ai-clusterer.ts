import OpenAI from 'openai';
import { z } from 'zod';
import {
  FetchedListing,
  NormalizationInput,
  NormalizationOutput,
  NormalizedListing,
} from '../types-v4.js';
import {
  PRICING_V4_NORMALIZATION_SYSTEM_PROMPT,
  buildPricingV4NormalizationPrompt,
} from '../prompts-v4.js';

const aiResultSchema = z.array(
  z.object({
    listingId: z.number().int().min(0),
    isMatch: z.boolean(),
    matchConfidence: z.enum(['HIGH', 'MED', 'LOW']),
    reason: z.string().max(20),
    normalizedCondition: z.enum(['NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR']).nullable(),
  })
);

type AiClustererOptions = {
  enabled: boolean;
  openaiApiKey?: string;
  openaiModel: string;
  timeoutMs: number;
  client?: OpenAI;
};

export class AiClusterer {
  private readonly client: OpenAI | null;

  constructor(private readonly options: AiClustererOptions) {
    if (options.client) {
      this.client = options.client;
    } else if (options.openaiApiKey) {
      this.client = new OpenAI({ apiKey: options.openaiApiKey, timeout: options.timeoutMs });
    } else {
      this.client = null;
    }
  }

  async normalize(input: NormalizationInput): Promise<NormalizationOutput> {
    if (!this.options.enabled || !this.client) {
      return this.passThrough(input.listings, 'AI normalization disabled');
    }

    const indexed = input.listings.map((listing, index) => ({
      id: index,
      listing,
    }));
    const prompt = buildPricingV4NormalizationPrompt({
      brand: input.targetBrand,
      model: input.targetModel,
      productType: input.targetProductType,
      listings: indexed,
    });

    try {
      const completion = await this.client.chat.completions.create({
        model: this.options.openaiModel,
        messages: [
          { role: 'system', content: PRICING_V4_NORMALIZATION_SYSTEM_PROMPT },
          { role: 'user', content: prompt },
        ],
        temperature: 0,
        max_tokens: 500,
        response_format: { type: 'json_object' },
      });

      const responseText = completion.choices[0]?.message?.content;
      if (!responseText) {
        return this.passThrough(input.listings, 'AI returned empty response');
      }

      const parsed = this.parseResponse(responseText);
      if (!parsed) {
        return this.passThrough(input.listings, 'AI response parse failed');
      }

      return this.applyMatches(input.listings, parsed);
    } catch (error) {
      return this.passThrough(
        input.listings,
        error instanceof Error ? error.message : 'AI normalization failed'
      );
    }
  }

  private parseResponse(responseText: string): z.infer<typeof aiResultSchema> | null {
    try {
      const parsed = JSON.parse(responseText);
      if (Array.isArray(parsed)) {
        const validated = aiResultSchema.safeParse(parsed);
        return validated.success ? validated.data : null;
      }
      if (Array.isArray((parsed as any)?.results)) {
        const validated = aiResultSchema.safeParse((parsed as any).results);
        return validated.success ? validated.data : null;
      }
      return null;
    } catch {
      return null;
    }
  }

  private applyMatches(
    listings: FetchedListing[],
    matches: z.infer<typeof aiResultSchema>
  ): NormalizationOutput {
    const byId = new Map(matches.map((match) => [match.listingId, match]));
    const relevant: NormalizedListing[] = [];
    const excluded: { listing: FetchedListing; reason: string }[] = [];

    listings.forEach((listing, index) => {
      const match = byId.get(index);
      if (!match) {
        relevant.push(listing);
        return;
      }

      if (match.isMatch) {
        relevant.push({
          ...listing,
          matchConfidence: match.matchConfidence,
          normalizedCondition: match.normalizedCondition,
        });
      } else {
        excluded.push({ listing, reason: match.reason });
      }
    });

    return {
      relevantListings: relevant,
      excludedListings: excluded,
      clusterSummary: `AI filtered ${excluded.length} of ${listings.length}`,
    };
  }

  private passThrough(listings: FetchedListing[], reason: string): NormalizationOutput {
    return {
      relevantListings: listings,
      excludedListings: [],
      clusterSummary: reason,
    };
  }
}
