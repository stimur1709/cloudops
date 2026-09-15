export function scopedPreviousData<T>(scopeKey: readonly (string | number)[]) {
  return (
    previousData: T | undefined,
    previousQuery: { queryKey: readonly unknown[] } | undefined,
  ): T | undefined => {
    if (!previousQuery || previousData === undefined) return undefined;
    const previousKey = previousQuery.queryKey;
    if (previousKey.length < scopeKey.length) return undefined;
    return scopeKey.every((value, index) =>
      Object.is(previousKey[index], value),
    )
      ? previousData
      : undefined;
  };
}
