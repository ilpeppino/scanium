# Multilingual AI Assistant - Developer Guide

## Overview

This guide documents the multilingual AI output enforcement system implemented to ensure
AI-generated content (ready-to-sell descriptions, titles, etc.) is fully language-consistent with
the user's selected app language.

## Architecture

### Language Enforcement Flow

```
[Android App]                    [Backend]
     │                                │
     ├─ User selects language (IT) ───►
     │                                │
     ├─ Build request with ────────────►
     │  - assistantPrefs.language = "IT"
     │  - attribute values
     │  - user message
     │                                │
     │                    ┌───────────┴───────────┐
     │                    │                       │
     │               [Localized Prompts]    [Language Check]
     │               System prompt in IT    Stopword detection
     │               User prompt in IT      Metrics recording
     │               Attribute labels IT
     │                    │                       │
     │                    └───────────┬───────────┘
     │                                │
     │                          [LLM Call]
     │                     OpenAI/Claude with
     │                     fully localized prompt
     │                                │
     │                    ┌───────────┴───────────┐
     │                    │                       │
     │               [Response]         [Language Verify]
     │               Italian content    Check response lang
     │                    │                       │
     │                    └───────────┬───────────┘
     │                                │
◄────┴────────────────────────────────┘
     Response in Italian
```

## Key Files

### Backend

| File                                                           | Purpose                                             |
|----------------------------------------------------------------|-----------------------------------------------------|
| `backend/src/modules/assistant/prompts/prompt-localization.ts` | Complete localized prompt templates for 7 languages |
| `backend/src/modules/assistant/prompts/listing-generation.ts`  | Prompt building with localization                   |
| `backend/src/modules/assistant/language-consistency.ts`        | Stopword-based language detection                   |
| `backend/src/modules/assistant/openai-provider.ts`             | OpenAI integration with language verification       |
| `backend/src/modules/assistant/claude-provider.ts`             | Claude integration with language verification       |

### Android

| File                                                                  | Purpose                                              |
|-----------------------------------------------------------------------|------------------------------------------------------|
| `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` | `assistantLanguageFlow` - user's language preference |
| `androidApp/src/main/res/values-{lang}/strings.xml`                   | Localized UI strings                                 |

## Supported Languages

| Code  | Language            | Native Name          |
|-------|---------------------|----------------------|
| EN    | English             | English              |
| NL    | Dutch               | Nederlands           |
| DE    | German              | Deutsch              |
| FR    | French              | Français             |
| IT    | Italian             | Italiano             |
| ES    | Spanish             | Español              |
| PT_BR | Portuguese (Brazil) | Português Brasileiro |

## Debugging

### Android Logcat Filter

To see language-related logs on Android:

```bash
adb logcat -s "AssistantViewModel" | grep -E "(language|lang|prefs)"
```

### Backend DEV Logging

In development mode (`NODE_ENV=development`), language mismatches are logged:

```bash
# Filter for language mismatch warnings
grep -E "(LANG_MISMATCH|RESPONSE_LANG_MISMATCH)" backend.log
```

Log format:

```json
{
  "correlationId": "abc-123",
  "expectedLanguage": "IT",
  "detectedLanguage": "EN",
  "confidence": 0.75,
  "message": "Language consistency check failed"
}
```

### Metrics

Language mismatch metrics are tracked in-memory:

```typescript
import { getLanguageMetrics } from './language-consistency.js';

const metrics = getLanguageMetrics();
// {
//   requestsChecked: 100,
//   attributeMismatches: 2,
//   promptMismatches: 0,
//   responseMismatches: 5
// }
```

## Manual Test Plan

### Prerequisites

1. Build and install the app
2. Ensure backend is running
3. Have network connectivity

### Test Case 1: Italian Language

1. **Setup**
    - Open Settings > General > Language
    - Select "Italiano"
    - Open Settings > AI Assistant > Language
    - Confirm it's set to "Italiano"

2. **Test**
    - Navigate to camera screen
    - Scan an item (e.g., a book or electronic device)
    - Tap "Ask AI" or navigate to assistant
    - Type: "Genera una descrizione pronta per la vendita"
    - OR tap the ready-to-sell quick action

3. **Verify**
    - [ ] System prompt shown in logs should be in Italian
    - [ ] Attribute labels in prompt are Italian (Marca, Modello, Colore)
    - [ ] AI response is ENTIRELY in Italian
    - [ ] No English words in title or description
    - [ ] Title format is correct (max 80 chars)
    - [ ] Bullet points in description are in Italian

4. **Expected Response Example**
   ```
   Titolo: Dell XPS 13 Laptop - 16GB RAM, Ottime Condizioni

   Descrizione:
   Laptop Dell XPS 13 in ottime condizioni, perfetto per lavoro e studio.

   • Processore Intel Core i7
   • 16GB di RAM
   • Schermo 13 pollici Full HD
   • Batteria con buona autonomia
   • Condizione: Usato - Ottime Condizioni
   ```

### Test Case 2: German Language

1. **Setup**
    - Set app language to "Deutsch"
    - Set assistant language to "Deutsch"

2. **Test**
    - Scan an item
    - Request: "Erstelle eine Produktbeschreibung"

3. **Verify**
    - [ ] Response is entirely in German
    - [ ] Attribute labels are German (Marke, Modell, Farbe)
    - [ ] No English leakage

### Test Case 3: Language Switching

1. **Test**
    - Generate a listing in Italian
    - Change language to French
    - Generate another listing for the same item

2. **Verify**
    - [ ] First response is in Italian
    - [ ] Second response is in French
    - [ ] No mixing of languages

### Test Case 4: Edge Cases

1. **Very Short Input**
    - Send just "OK" or "Si"
    - Verify response is still in selected language

2. **Technical Terms**
    - Item with technical specs (RAM, CPU, etc.)
    - Verify technical terms are not translated incorrectly

3. **Brand Names**
    - Item with brand name (Nike, Apple, IKEA)
    - Verify brand names are preserved as-is

## Adding a New Language

To add support for a new language:

1. **Backend: Add localized content**
   Edit `backend/src/modules/assistant/prompts/prompt-localization.ts`:
   ```typescript
   const XX_CONTENT: LocalizedPromptContent = {
     languageEnforcement: '...',
     roleDescription: '...',
     // ... all fields
   };

   export const LOCALIZED_CONTENT: Record<SupportedLanguage, LocalizedPromptContent> = {
     // ...
     XX: XX_CONTENT,
   };
   ```

2. **Backend: Add stopwords**
   Edit `backend/src/modules/assistant/language-consistency.ts`:
   ```typescript
   const STOPWORDS: Record<string, Set<string>> = {
     // ...
     XX: new Set([/* common stopwords */]),
   };
   ```

3. **Android: Add strings**
   Create `androidApp/src/main/res/values-xx/strings.xml` with all translations.

4. **Android: Add language option**
   Edit settings to include the new language option.

5. **Tests**
   Add tests for the new language in:
    - `listing-generation.test.ts`
    - `language-consistency.test.ts`

## Troubleshooting

### Issue: AI response contains English

**Cause**: System prompt or user prompt may have leaked English content.

**Debug**:

1. Check backend logs for the actual prompt sent
2. Verify `assistantPrefs.language` is correct in request
3. Check if attribute values are being sent in English

**Fix**: Ensure all prompt construction uses `getLocalizedContent(language)`.

### Issue: Attribute labels in wrong language

**Cause**: Language parameter not being passed to `buildListingUserPrompt`.

**Debug**:

1. Check provider code passes language to prompt builder
2. Verify `getLocalizedAttributeLabel` is being called

### Issue: Low confidence language detection

**Cause**: Short text or mixed content.

**Note**: Language detection is best-effort for logging/metrics. The prompt localization ensures
correct output regardless of detection accuracy.

## Performance Considerations

- Stopword detection is O(n) where n = word count
- Prompt localization adds no overhead (static content)
- Language metrics are in-memory (reset on server restart)

## Future Improvements

1. **Output Retry**: If response language doesn't match, auto-retry with stronger enforcement
2. **Client-side Validation**: Add Android-side language check before displaying
3. **Persistent Metrics**: Store metrics in database for monitoring dashboards
4. **Additional Languages**: Add support for more languages (Chinese, Japanese, Korean, etc.)
