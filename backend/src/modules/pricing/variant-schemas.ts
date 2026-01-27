export type VariantField = {
  key: string;
  label: string;
  type: 'select' | 'text';
  options?: string[];
  required: boolean;
};

export type VariantSchema = {
  fields: VariantField[];
  completenessOptions: string[];
};

const ELECTRONICS_COMPLETENESS = [
  'Charger',
  'Original box',
  'Manual',
  'Accessories',
  'Receipt',
];

const FASHION_COMPLETENESS = ['Original box', 'Dust bag', 'Tags', 'Receipt'];

const VARIANT_SCHEMAS: Array<{
  id: string;
  patterns: RegExp[];
  schema: VariantSchema;
}> = [
  {
    id: 'laptop',
    patterns: [/laptop/i, /notebook/i, /macbook/i],
    schema: {
      fields: [
        {
          key: 'storage',
          label: 'Storage',
          type: 'select',
          options: ['128GB', '256GB', '512GB', '1TB', '2TB'],
          required: false,
        },
        {
          key: 'ram',
          label: 'Memory (RAM)',
          type: 'select',
          options: ['4GB', '8GB', '16GB', '32GB', '64GB'],
          required: false,
        },
        {
          key: 'cpu',
          label: 'Processor',
          type: 'text',
          required: false,
        },
        {
          key: 'screenSize',
          label: 'Screen size',
          type: 'text',
          required: false,
        },
      ],
      completenessOptions: ELECTRONICS_COMPLETENESS,
    },
  },
  {
    id: 'smartphone',
    patterns: [/smartphone/i, /phone/i, /iphone/i, /android/i],
    schema: {
      fields: [
        {
          key: 'storage',
          label: 'Storage',
          type: 'select',
          options: ['64GB', '128GB', '256GB', '512GB', '1TB'],
          required: false,
        },
        {
          key: 'color',
          label: 'Color',
          type: 'text',
          required: false,
        },
        {
          key: 'carrier',
          label: 'Carrier / Lock status',
          type: 'text',
          required: false,
        },
      ],
      completenessOptions: ELECTRONICS_COMPLETENESS,
    },
  },
  {
    id: 'tablet',
    patterns: [/tablet/i, /ipad/i],
    schema: {
      fields: [
        {
          key: 'storage',
          label: 'Storage',
          type: 'select',
          options: ['64GB', '128GB', '256GB', '512GB', '1TB'],
          required: false,
        },
        {
          key: 'connectivity',
          label: 'Connectivity',
          type: 'select',
          options: ['Wi-Fi', 'Wi-Fi + Cellular'],
          required: false,
        },
        {
          key: 'color',
          label: 'Color',
          type: 'text',
          required: false,
        },
      ],
      completenessOptions: ELECTRONICS_COMPLETENESS,
    },
  },
  {
    id: 'console',
    patterns: [/console/i, /playstation/i, /ps[0-9]/i, /xbox/i, /nintendo/i, /switch/i],
    schema: {
      fields: [
        {
          key: 'storage',
          label: 'Storage',
          type: 'select',
          options: ['128GB', '256GB', '512GB', '1TB', '2TB'],
          required: false,
        },
        {
          key: 'edition',
          label: 'Edition',
          type: 'text',
          required: false,
        },
      ],
      completenessOptions: ELECTRONICS_COMPLETENESS,
    },
  },
  {
    id: 'camera',
    patterns: [/camera/i, /dslr/i, /mirrorless/i],
    schema: {
      fields: [
        {
          key: 'lensMount',
          label: 'Lens mount',
          type: 'select',
          options: ['Canon EF', 'Canon RF', 'Nikon F', 'Nikon Z', 'Sony E', 'Fuji X', 'MFT'],
          required: false,
        },
        {
          key: 'megapixels',
          label: 'Megapixels',
          type: 'text',
          required: false,
        },
      ],
      completenessOptions: ELECTRONICS_COMPLETENESS,
    },
  },
  {
    id: 'headphones',
    patterns: [/headphone/i, /earphone/i, /earbud/i],
    schema: {
      fields: [
        {
          key: 'type',
          label: 'Type',
          type: 'select',
          options: ['Over-ear', 'On-ear', 'In-ear'],
          required: false,
        },
        {
          key: 'wireless',
          label: 'Connectivity',
          type: 'select',
          options: ['Wired', 'Wireless'],
          required: false,
        },
      ],
      completenessOptions: ELECTRONICS_COMPLETENESS,
    },
  },
  {
    id: 'watch',
    patterns: [/watch/i, /smartwatch/i],
    schema: {
      fields: [
        {
          key: 'caseSize',
          label: 'Case size',
          type: 'select',
          options: ['38mm', '40mm', '41mm', '42mm', '44mm', '45mm'],
          required: false,
        },
        {
          key: 'material',
          label: 'Material',
          type: 'text',
          required: false,
        },
      ],
      completenessOptions: ELECTRONICS_COMPLETENESS,
    },
  },
  {
    id: 'clothing',
    patterns: [/clothing/i, /apparel/i, /jacket/i, /coat/i, /shirt/i, /dress/i],
    schema: {
      fields: [
        {
          key: 'size',
          label: 'Size',
          type: 'select',
          options: ['XS', 'S', 'M', 'L', 'XL', 'XXL'],
          required: false,
        },
        {
          key: 'color',
          label: 'Color',
          type: 'text',
          required: false,
        },
      ],
      completenessOptions: FASHION_COMPLETENESS,
    },
  },
  {
    id: 'shoes',
    patterns: [/shoe/i, /sneaker/i, /boot/i],
    schema: {
      fields: [
        {
          key: 'size',
          label: 'Size',
          type: 'text',
          required: false,
        },
        {
          key: 'color',
          label: 'Color',
          type: 'text',
          required: false,
        },
      ],
      completenessOptions: FASHION_COMPLETENESS,
    },
  },
];

const DEFAULT_SCHEMA: VariantSchema = {
  fields: [],
  completenessOptions: ELECTRONICS_COMPLETENESS,
};

export function getVariantSchema(productType: string | undefined | null): VariantSchema {
  if (!productType) {
    return DEFAULT_SCHEMA;
  }
  const normalized = productType.toLowerCase();
  const match = VARIANT_SCHEMAS.find((entry) => entry.patterns.some((pattern) => pattern.test(normalized)));
  return match?.schema ?? DEFAULT_SCHEMA;
}

export const VARIANT_SCHEMA_IDS = VARIANT_SCHEMAS.map((entry) => entry.id);
