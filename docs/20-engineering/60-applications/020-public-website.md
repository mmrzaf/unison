# Public Website engineering

The Public Website is a static-first bilingual Astro application with governed content, localized URLs and metadata, explicit SEO and performance contracts, minimal client JavaScript, privacy-bounded analytics, and narrow public forms.

## Give every public page an explicit stable language identity

Give every public page an explicit stable language identity.

Persian is the default public edition. The canonical Persian home is `/`, `/fa/` remains a stable Persian alias, and the English home is `/en/`. All non-root localized routes use explicit locale prefixes:

```text
/
/fa/
/fa/...
/en/
/en/...
```

The root path must render the Persian home directly and must not show a language-choice gateway. The `/fa/` alias declares `/` as its canonical URL and is excluded from the sitemap as a duplicate home URL.

Every localized page declares its locale and translated counterpart. English pages use left-to-right presentation. Persian pages use right-to-left presentation and Persian-appropriate typography and layout.

## Keep language choice explicit and prevent redirect loops or hidden localized pages

Waiotech must keep language choice explicit and prevent redirect loops or hidden localized pages.

The header provides a direct language switch between the corresponding Persian and English pages. The site does not use automatic locale redirection. Both language editions remain directly accessible, and canonical localized pages remain indexable.

## Treat localized metadata as part of the page contract

Waiotech must treat localized metadata as part of the page contract.

Every indexable public page provides localized:

- title;
- description;
- heading structure;
- canonical URL;
- reciprocal `hreflang` references;
- `x-default` where applicable;
- Open Graph metadata;
- social preview text and image alternative text;
- structured-data fields;
- breadcrumb labels where applicable.

## Technically complete SEO

Waiotech must enforce complete SEO inputs while making no unsupported ranking guarantee.

It means Waiotech implements every controllable technical and editorial discoverability requirement, including:

- semantic HTML;
- useful original content;
- stable readable URLs;
- correct titles and descriptions;
- canonical links;
- reciprocal locale links;
- XML sitemaps;
- robots directives;
- structured data matching visible content;
- Open Graph and social metadata;
- descriptive internal links;
- optimized media;
- redirect management;
- correct not-found behavior;
- accessible heading hierarchy;
- strong measured performance;
- no duplicate, empty, or misleading localized pages.

External search ranking is not treated as a controllable test result.

## Use structured data as truthful machine-readable content, not promotional markup abuse

Waiotech must use structured data as truthful machine-readable content, not promotional markup abuse.

It may publish schema types such as `Organization`, `SoftwareApplication`, `WebSite`, `BreadcrumbList`, `Article`, `FAQPage`, and `ContactPage` only when the visible page content truthfully satisfies the type.

## Generate sitemaps from the governed publication state

Waiotech must generate sitemaps from the governed publication state.

Sitemaps are generated from accepted indexable content. They include canonical localized URLs and exclude drafts, private content, redirects, error pages, and non-indexable utility routes.

## Control indexing explicitly while protecting private content through authentication and network boundaries

Control indexing explicitly while protecting private content through authentication and network boundaries.

Robots directives are source-controlled and environment-aware. Non-public deployments block indexing. The canonical public deployment permits only intended public content.

Robots directives are not a security boundary.

## Preserve public link integrity through governed redirects

Waiotech must preserve public link integrity through governed redirects.

Replaced public URLs receive explicit permanent redirects to the closest semantically equivalent canonical page. Removed content with no replacement returns the correct terminal response and is removed from navigation and sitemap.

Redirect chains and redirect loops are prohibited.

## Make public performance a build and release concern

Waiotech must make public performance a build and release concern.

Public pages are static-first, cacheable, media-optimized, and free of unnecessary client JavaScript. Performance budgets cover HTML, CSS, JavaScript, fonts, images, layout stability, and interaction latency.

Measured thresholds belong in the website Experience Contract and release tests.

## Optimize public media without weakening accessibility or content meaning

Optimize public media without weakening accessibility or content meaning.

Images use responsive sizes, modern supported formats, intrinsic dimensions, meaningful alternative text, lazy loading where appropriate, and content-derived filenames or identifiers. Decorative images use empty alternative text.

## Provide stable bilingual typography without avoidable tracking or rendering risk

Waiotech must provide stable bilingual typography without avoidable tracking or rendering risk.

English and Persian typography use licensed, locally governed or privacy-safe font delivery with bounded font files, subset strategy where valid, fallback stacks, and no layout-breaking dependency on third-party font services.

## Treat public content as typed source-controlled material

Waiotech must treat public content as typed source-controlled material.

Content uses governed Astro collections with typed metadata such as:

- content identity;
- locale;
- translated counterpart;
- title;
- description;
- canonical path;
- publication condition;
- content class;
- structured-data type;
- social image;
- review ownership;
- public visibility.

## Publish public content through the same attributable review and release discipline as software

Waiotech must publish public content through the same attributable review and release discipline as software.

Content changes use source control, typed content validation, language review, product-claim review, link and metadata checks, Astro build, preview verification, and immutable artifact release. Security, privacy, legal, or compliance claims require review by the applicable owner.

## Keep public content versioned, reviewable, and reproducible

Waiotech must keep public content versioned, reviewable, and reproducible.

A contribution interface may prepare source changes, but accepted public content must resolve to a versioned reviewed source state and immutable website artifact. Runtime content must not bypass bilingual, authority, security, and SEO validation.

## Make public product claims traceable to canonical meaning

Waiotech must make public product claims traceable to canonical meaning.

Product-significant pages identify their owning content review and applicable Product Authority concepts through metadata or review evidence. The relationship need not be displayed publicly, but it must be available during review and correction.

## Prevent accidental partial bilingual publication

Waiotech must prevent accidental partial bilingual publication.

The build fails for content classes requiring bilingual parity. An explicitly language-specific article may be published only when its metadata and user experience identify that limitation without creating a broken counterpart.

## Keep public claims authority-aligned

Waiotech must keep public claims authority-aligned.

Public content validation and review must identify the Product Authority basis for product-significant claims.

## Measure public content without creating an unnecessary identity graph

Measure public content without creating an unnecessary identity graph.

Analytics must be privacy-minimized and limited to public content performance, navigation, campaign attribution, and technical quality. It must not create cross-surface user tracking, ingest Tenant data, or weaken consent and privacy obligations.

## Keep public measurement minimal, transparent, and separate from product identity

Waiotech must keep public measurement minimal, transparent, and separate from product identity.

The Public Website may store language choice, accessibility preferences, and other bounded presentation settings. Privacy-safe aggregate analytics may operate under the applicable privacy contract.

Advertising trackers, cross-site behavioral profiling, Tenant identity correlation, and collection of authenticated product activity are prohibited. Non-essential storage or measurement requiring consent must remain disabled until valid consent exists.

## Separate public browsing from authenticated product sessions

Waiotech must separate public browsing from authenticated product sessions.

It may link to the Dashboard authentication entry, but public-origin scripts and storage do not receive or inspect authenticated Dashboard session credentials or Tenant context.

## Keep public submission bounded and non-authoritative

Waiotech must keep public submission bounded and non-authoritative.

Contact or support-entry forms use typed narrow endpoints with input validation, size limits, abuse controls, safe acknowledgement, privacy notice, and no product authority. Form failure must not expose provider or infrastructure details.

## Related documents
- [Public Website](../../30-experience/020-public-website.md)
- [Application surface architecture](010-application-surface-architecture.md)
- [Documentation, help, and learning engineering](050-documentation-help-and-learning.md)
