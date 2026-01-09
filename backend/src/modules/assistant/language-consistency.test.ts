import { describe, it, expect, beforeEach } from 'vitest';
import {
  detectLanguage,
  checkLanguageConsistency,
  checkResponseLanguage,
  recordLanguageCheck,
  getLanguageMetrics,
  resetLanguageMetrics,
} from './language-consistency.js';

describe('Language Consistency', () => {
  describe('detectLanguage', () => {
    it('detects English text', () => {
      const result = detectLanguage(
        'This is a test of the language detection system. It should correctly identify this text as English.'
      );
      expect(result.detectedLanguage).toBe('EN');
      expect(result.confidence).toBeGreaterThan(0.3);
    });

    it('detects Italian text', () => {
      const result = detectLanguage(
        'Questo è un test del sistema di rilevamento della lingua. Dovrebbe identificare correttamente questo testo come italiano.'
      );
      expect(result.detectedLanguage).toBe('IT');
      expect(result.confidence).toBeGreaterThan(0.3);
    });

    it('detects German text', () => {
      const result = detectLanguage(
        'Dies ist ein Test des Spracherkennungssystems. Es sollte diesen Text korrekt als Deutsch erkennen.'
      );
      expect(result.detectedLanguage).toBe('DE');
      expect(result.confidence).toBeGreaterThan(0.3);
    });

    it('detects Dutch text', () => {
      const result = detectLanguage(
        'Dit is een test van het taaldetectiesysteem. Het zou deze tekst correct als Nederlands moeten identificeren.'
      );
      expect(result.detectedLanguage).toBe('NL');
      expect(result.confidence).toBeGreaterThan(0.3);
    });

    it('detects French text', () => {
      const result = detectLanguage(
        "Ceci est un test du système de détection de langue. Il devrait identifier correctement ce texte comme français."
      );
      expect(result.detectedLanguage).toBe('FR');
      expect(result.confidence).toBeGreaterThan(0.3);
    });

    it('detects Spanish text', () => {
      const result = detectLanguage(
        'Esta es una prueba del sistema de detección de idiomas. Debería identificar correctamente este texto como español.'
      );
      expect(result.detectedLanguage).toBe('ES');
      expect(result.confidence).toBeGreaterThan(0.3);
    });

    it('detects Portuguese text', () => {
      const result = detectLanguage(
        'Este é um teste do sistema de detecção de idiomas. Deve identificar corretamente este texto como português.'
      );
      expect(result.detectedLanguage).toBe('PT_BR');
      expect(result.confidence).toBeGreaterThan(0.3);
    });

    it('returns matchesExpected correctly', () => {
      const italianText = 'Questo è un test della funzione di rilevamento.';

      const resultIT = detectLanguage(italianText, 'IT');
      expect(resultIT.matchesExpected).toBe(true);

      const resultEN = detectLanguage(italianText, 'EN');
      expect(resultEN.matchesExpected).toBe(false);
    });

    it('handles very short text', () => {
      const result = detectLanguage('OK');
      expect(result.detectedLanguage).toBeDefined();
      expect(result.confidence).toBeDefined();
    });

    it('handles empty text', () => {
      const result = detectLanguage('');
      expect(result.detectedLanguage).toBeDefined();
      expect(result.confidence).toBe(0);
    });
  });

  describe('checkLanguageConsistency', () => {
    it('returns consistent for matching language', () => {
      const texts = [
        'This is the first segment in English.',
        'This is another segment also in English.',
        'The third segment continues in the same language.',
      ];

      const result = checkLanguageConsistency(texts, 'EN');
      expect(result.isConsistent).toBe(true);
      expect(result.expectedLanguage).toBe('EN');
    });

    it('returns inconsistent for mismatched language', () => {
      const texts = [
        'Questo è un testo in italiano.',
        'Anche questo è scritto in italiano.',
        'E questo è il terzo segmento italiano.',
      ];

      const result = checkLanguageConsistency(texts, 'EN');
      expect(result.isConsistent).toBe(false);
      expect(result.detectedLanguage).toBe('IT');
      expect(result.warningMessage).toContain('Language mismatch');
    });

    it('handles empty array', () => {
      const result = checkLanguageConsistency([], 'EN');
      expect(result.isConsistent).toBe(true);
      expect(result.segmentsChecked).toBe(0);
    });

    it('filters out short texts', () => {
      const texts = ['OK', 'Yes', 'A longer segment that should be checked'];

      const result = checkLanguageConsistency(texts, 'EN');
      expect(result.segmentsChecked).toBe(1); // Only the long segment
    });

    it('normalizes language codes', () => {
      const texts = ['This is English text for testing.'];

      const result1 = checkLanguageConsistency(texts, 'en');
      const result2 = checkLanguageConsistency(texts, 'EN');
      const result3 = checkLanguageConsistency(texts, 'pt_br');
      const result4 = checkLanguageConsistency(texts, 'PT-BR');

      expect(result1.expectedLanguage).toBe('EN');
      expect(result2.expectedLanguage).toBe('EN');
      expect(result3.expectedLanguage).toBe('PT_BR');
      expect(result4.expectedLanguage).toBe('PT_BR');
    });
  });

  describe('checkResponseLanguage', () => {
    it('correctly identifies matching response language', () => {
      const result = checkResponseLanguage(
        'Here is the marketplace listing you requested. The title is clear and the description includes all key features.',
        'EN'
      );
      expect(result.matchesExpected).toBe(true);
    });

    it('correctly identifies mismatched response language', () => {
      const result = checkResponseLanguage(
        "Ecco l'inserzione per il marketplace che hai richiesto. Il titolo è chiaro e la descrizione include tutte le caratteristiche principali.",
        'EN'
      );
      expect(result.matchesExpected).toBe(false);
      expect(result.detectedLanguage).toBe('IT');
    });
  });

  describe('Language Metrics', () => {
    beforeEach(() => {
      resetLanguageMetrics();
    });

    it('tracks attribute mismatches', () => {
      recordLanguageCheck('attribute', false);
      recordLanguageCheck('attribute', true);
      recordLanguageCheck('attribute', false);

      const metrics = getLanguageMetrics();
      expect(metrics.requestsChecked).toBe(3);
      expect(metrics.attributeMismatches).toBe(2);
    });

    it('tracks prompt mismatches', () => {
      recordLanguageCheck('prompt', false);
      recordLanguageCheck('prompt', true);

      const metrics = getLanguageMetrics();
      expect(metrics.promptMismatches).toBe(1);
    });

    it('tracks response mismatches', () => {
      recordLanguageCheck('response', false);

      const metrics = getLanguageMetrics();
      expect(metrics.responseMismatches).toBe(1);
    });

    it('resets metrics correctly', () => {
      recordLanguageCheck('attribute', false);
      recordLanguageCheck('prompt', false);
      recordLanguageCheck('response', false);

      resetLanguageMetrics();

      const metrics = getLanguageMetrics();
      expect(metrics.requestsChecked).toBe(0);
      expect(metrics.attributeMismatches).toBe(0);
      expect(metrics.promptMismatches).toBe(0);
      expect(metrics.responseMismatches).toBe(0);
    });
  });
});
