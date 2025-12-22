export type ErrorReportPayload = {
  message: string;
  correlationId?: string;
  url?: string;
  method?: string;
  statusCode?: number;
  stack?: string;
};

export interface ErrorReporter {
  report(payload: ErrorReportPayload): void;
}

class NoopErrorReporter implements ErrorReporter {
  report(_payload: ErrorReportPayload): void {
    // Intentionally no-op; hook for external error reporting providers.
  }
}

export const errorReporter: ErrorReporter = new NoopErrorReporter();
