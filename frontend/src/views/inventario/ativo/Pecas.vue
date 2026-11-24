<!-- Peças do ativo agrupadas por tipo, cada tipo com as colunas que o agente manda (campos do OCS / GLPI Agent). -->
<template>
  <div>
    <el-empty v-if="!grupos.length" :description="vazio" :image-size="60" />
    <div v-for="g in grupos" :key="g.tipo" class="mb-16px">
      <div class="mb-6px text-13px font-bold">
        {{ COMPONENTES[g.tipo] || g.tipo }}
        <span class="font-normal text-[var(--el-text-color-secondary)]"
          >({{ g.itens.length }})</span
        >
      </div>
      <el-table :data="g.itens" border size="small">
        <el-table-column
          v-for="(col, i) in colunas(g.tipo)"
          :key="col.rotulo"
          :label="col.rotulo"
          :min-width="col.largura ?? 120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <template v-if="col.uso">
              <el-progress
                v-if="uso(row) !== null"
                :percentage="uso(row)!"
                :status="uso(row)! >= 90 ? 'exception' : uso(row)! >= 75 ? 'warning' : undefined"
                :stroke-width="10"
              />
              <span v-else>—</span>
            </template>
            <template v-else>{{ valor(row, col, i === 0) }}</template>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { COMPONENTES, type Componente } from '@/api/inventario'

interface Coluna {
  rotulo: string
  campos: string[]
  /** mb: valor em MB legível; mhz; sim: 1/0 */
  formato?: 'mb' | 'mhz' | 'sim'
  largura?: number
  uso?: boolean
}

const props = defineProps<{ componentes: Componente[]; tipos: string[]; vazio: string }>()

const c = (rotulo: string, campos: string[], extra: Partial<Coluna> = {}): Coluna => ({
  rotulo,
  campos,
  ...extra
})

/** Colunas de cada tipo, pelos nomes de campo do agente OCS (o GLPI Agent é traduzido para eles). */
const COLUNAS: Record<string, Coluna[]> = {
  bios: [
    c('Fabricante', ['SMANUFACTURER'], { largura: 130 }),
    c('Modelo', ['SMODEL'], { largura: 150 }),
    c('Série', ['SSN']),
    c('Placa-mãe', ['MMANUFACTURER', 'MMODEL'], { largura: 160 }),
    c('BIOS', ['BMANUFACTURER', 'BVERSION'], { largura: 150 }),
    c('Data da BIOS', ['BDATE']),
    c('Etiqueta (asset tag)', ['ASSETTAG'])
  ],
  processador: [
    c('Modelo', ['TYPE', 'NAME'], { largura: 240 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Núcleos', ['CORES'], { largura: 80 }),
    c('Threads', ['LOGICAL_CPUS'], { largura: 80 }),
    c('Velocidade', ['SPEED'], { formato: 'mhz', largura: 100 }),
    c('Soquete', ['SOCKET'], { largura: 100 })
  ],
  memoria: [
    c('Slot', ['NUMSLOTS', 'CAPTION'], { largura: 80 }),
    c('Capacidade', ['CAPACITY'], { formato: 'mb', largura: 100 }),
    c('Tipo', ['TYPE'], { largura: 90 }),
    c('Velocidade', ['SPEED'], { formato: 'mhz', largura: 100 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Série', ['SERIALNUMBER']),
    c('Descrição', ['DESCRIPTION'], { largura: 150 })
  ],
  disco: [
    c('Modelo', ['MODEL', 'NAME'], { largura: 200 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Tamanho', ['DISKSIZE'], { formato: 'mb', largura: 100 }),
    c('Tipo', ['TYPE', 'INTERFACE'], { largura: 120 }),
    c('Série', ['SERIALNUMBER', 'SERIAL']),
    c('Firmware', ['FIRMWARE'], { largura: 100 })
  ],
  volume: [
    c('Unidade', ['LETTER', 'VOLUMN'], { largura: 110 }),
    c('Rótulo', ['VOLUMN', 'LABEL']),
    c('Sistema de arquivos', ['FILESYSTEM'], { largura: 130 }),
    c('Tipo', ['TYPE'], { largura: 130 }),
    c('Total', ['TOTAL'], { formato: 'mb', largura: 100 }),
    c('Livre', ['FREE'], { formato: 'mb', largura: 100 }),
    c('Uso', [], { uso: true, largura: 140 })
  ],
  video: [
    c('Placa', ['NAME'], { largura: 220 }),
    c('Chipset', ['CHIPSET'], { largura: 160 }),
    c('Memória', ['MEMORY'], { formato: 'mb', largura: 100 }),
    c('Resolução', ['RESOLUTION'])
  ],
  som: [
    c('Nome', ['NAME'], { largura: 220 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Descrição', ['DESCRIPTION'])
  ],
  controlador: [
    c('Nome', ['NAME'], { largura: 240 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Tipo', ['TYPE'])
  ],
  bateria: [
    c('Nome', ['NAME'], { largura: 160 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Química', ['CHEMISTRY']),
    c('Capacidade', ['CAPACITY', 'REAL_CAPACITY']),
    c('Série', ['SERIAL', 'SERIALNUMBER'])
  ],
  slot: [
    c('Nome', ['NAME', 'DESIGNATION'], { largura: 180 }),
    c('Descrição', ['DESCRIPTION'], { largura: 180 }),
    c('Situação', ['STATUS'])
  ],
  suprimento: [
    c('Suprimento', ['DESCRIPTION'], { largura: 220 }),
    c('Nível (%)', ['percentual'], { largura: 90 }),
    c('Nível', ['nivel']),
    c('Máximo', ['maximo'])
  ],
  rede: [
    c('Placa', ['DESCRIPTION'], { largura: 230 }),
    c('Tipo', ['TYPE'], { largura: 90 }),
    c('MAC', ['MACADDR'], { largura: 150 }),
    c('IP', ['IPADDRESS'], { largura: 130 }),
    c('Máscara', ['IPMASK'], { largura: 130 }),
    c('Gateway', ['IPGATEWAY'], { largura: 130 }),
    c('DHCP', ['IPDHCP'], { largura: 120 }),
    c('Velocidade', ['SPEED'], { largura: 100 }),
    c('Situação', ['STATUS'], { largura: 90 }),
    c('Virtual', ['VIRTUALDEV'], { formato: 'sim', largura: 70 })
  ],
  monitor: [
    c('Modelo', ['CAPTION'], { largura: 180 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Descrição', ['DESCRIPTION'], { largura: 160 }),
    c('Tipo', ['TYPE']),
    c('Série', ['SERIAL'])
  ],
  entrada: [
    c('Tipo', ['TYPE'], { largura: 100 }),
    c('Descrição', ['DESCRIPTION', 'CAPTION'], { largura: 220 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Interface', ['INTERFACE', 'POINTTYPE'])
  ],
  usb: [
    c('Dispositivo', ['NAME', 'DESCRIPTION'], { largura: 230 }),
    c('Fabricante', ['MANUFACTURER']),
    c('Fornecedor / produto', ['VENDORID', 'PRODUCTID']),
    c('Série', ['SERIAL'])
  ],
  impressora: [
    c('Nome', ['NAME'], { largura: 200 }),
    c('Driver', ['DRIVER'], { largura: 200 }),
    c('Porta', ['PORT'], { largura: 140 }),
    c('Rede', ['NETWORK'], { formato: 'sim', largura: 70 }),
    c('Compartilhada', ['SHARED'], { formato: 'sim', largura: 110 })
  ],
  modem: [
    c('Nome', ['NAME', 'DESCRIPTION'], { largura: 200 }),
    c('Modelo', ['MODEL']),
    c('Tipo', ['TYPE'])
  ]
}

const grupos = computed(() =>
  props.tipos
    .map((tipo) => ({ tipo, itens: props.componentes.filter((x) => x.tipo === tipo) }))
    .filter((g) => g.itens.length)
)
const colunas = (tipo: string) => COLUNAS[tipo] ?? [c('Descrição', [], { largura: 300 })]

const mb = (v: number) =>
  v >= 1048576
    ? `${(v / 1048576).toFixed(1)} TB`
    : v >= 1024
      ? `${(v / 1024).toFixed(v >= 10240 ? 0 : 1)} GB`
      : `${v} MB`

const valor = (row: Componente, col: Coluna, primeira: boolean) => {
  const partes = col.campos
    .map((k) => row.dados?.[k])
    .filter((v) => v !== undefined && v !== null && `${v}`.trim() !== '')
  if (!partes.length) return primeira ? row.descricao || '—' : '—'
  const v = col.formato === 'sim' ? partes[0] : partes.join(' ')
  const n = Number(String(v).replace(',', '.'))
  switch (col.formato) {
    case 'mb':
      return isNaN(n) ? v : mb(n)
    case 'mhz':
      return isNaN(n) ? v : `${n} MHz`
    case 'sim':
      return ['1', 'true', 'yes'].includes(String(v).toLowerCase()) ? 'Sim' : 'Não'
    default:
      return v
  }
}

const uso = (row: Componente): number | null => {
  const total = Number(row.dados?.TOTAL)
  const livre = Number(row.dados?.FREE)
  if (!total || isNaN(livre)) return null
  return Math.min(100, Math.max(0, Math.round(((total - livre) / total) * 100)))
}
</script>
