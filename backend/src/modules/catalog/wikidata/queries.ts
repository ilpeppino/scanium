export function buildStrictQuery(brandQids: string[], modelClassQids: string[]) {
  const brands = brandQids.map(q => `wd:${q}`).join(" ");
  const klass  = modelClassQids.map(q => `wd:${q}`).join(" ");

  return `
SELECT ?brand ?model ?modelLabel WHERE {
  VALUES ?brand { ${brands} }
  VALUES ?klass { ${klass} }

  ?model wdt:P31/wdt:P279* ?klass ;
         wdt:P176 ?brand .

  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}
ORDER BY ?brand ?modelLabel
`.trim();
}

export function buildBroadQuery(brandQids: string[], modelClassQids: string[]) {
  const brands = brandQids.map(q => `wd:${q}`).join(" ");
  const klass  = modelClassQids.map(q => `wd:${q}`).join(" ");

  return `
SELECT ?brand ?model ?modelLabel WHERE {
  VALUES ?brand { ${brands} }
  VALUES ?klass { ${klass} }

  ?model wdt:P31/wdt:P279* ?klass .

  { ?model wdt:P176 ?brand . }
  UNION
  { ?model wdt:P1716 ?brand . }

  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}
ORDER BY ?brand ?modelLabel
`.trim();
}
