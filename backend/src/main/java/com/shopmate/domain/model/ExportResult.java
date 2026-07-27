package com.shopmate.domain.model;

import java.util.List;

public record ExportResult(int added, int skipped, List<ExportFailure> failures) {}
