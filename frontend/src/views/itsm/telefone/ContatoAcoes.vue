<!--
  Formas de falar com um contato. Com o link da unidade fora, ramal e fixo VoIP param junto: celular e WhatsApp vêm
  primeiro e os outros ficam apagados, com o motivo no título.
-->
<template>
  <div class="acoes">
    <template v-for="a in acoes" :key="a.rotulo + a.texto">
      <a
        v-if="a.href"
        :class="['acao', `acao--${a.tipo}`, { 'acao--incerta': a.incerta }]"
        :href="a.href"
        :rel="a.tipo === 'whatsapp' ? 'noopener noreferrer' : undefined"
        :target="a.tipo === 'whatsapp' ? '_blank' : undefined"
        :title="a.incerta ? 'Link da unidade fora: pode não funcionar' : a.rotulo"
      >
        <Icon :icon="a.icone" :size="13" /> {{ a.texto }}
      </a>
      <span
        v-else
        :class="['acao', { 'acao--incerta': a.incerta }]"
        :title="a.incerta ? 'Link da unidade fora: pode não funcionar' : a.rotulo"
      >
        <Icon :icon="a.icone" :size="13" /> {{ a.texto }}
      </span>
    </template>
    <span v-if="t.celularOculto" class="oculto" title="O celular é visível só para a equipe de TI">
      Celular restrito à TI
    </span>
  </div>
</template>

<script lang="ts" setup>
import type { Telefone } from '@/api/itsm/telefone'

const props = defineProps<{ t: Telefone; linkFora?: boolean }>()

/** O wa.me pede o número com o código do país, só com dígitos */
const numeroWhatsapp = (n: string) => {
  const d = n.replace(/\D/g, '')
  return d.length <= 11 ? `55${d}` : d
}
const linkTel = (n: string) => `tel:${n.replace(/[^0-9+]/g, '')}`

const acoes = computed(() => {
  const { t } = props
  const fora = !!props.linkFora || !!t.linkFora
  const moveis = [
    t.celular && {
      tipo: 'celular',
      rotulo: 'Celular',
      texto: t.celular,
      icone: 'ep:iphone',
      href: linkTel(t.celular),
      incerta: false
    },
    t.celular &&
      t.whatsapp && {
        tipo: 'whatsapp',
        rotulo: 'Abrir conversa no WhatsApp',
        texto: 'WhatsApp',
        icone: 'ep:chat-dot-round',
        href: `https://wa.me/${numeroWhatsapp(t.celular)}`,
        incerta: false
      }
  ]
  const fixos = [
    t.ramal && {
      tipo: 'ramal',
      rotulo: 'Ramal',
      texto: `Ramal ${t.ramal}`,
      icone: 'ep:phone',
      href: '',
      incerta: fora
    },
    t.telefone && {
      tipo: 'fixo',
      rotulo: 'Telefone',
      texto: t.telefone,
      icone: 'ep:phone',
      href: linkTel(t.telefone),
      incerta: fora
    }
  ]
  return (fora ? [...moveis, ...fixos] : [...fixos, ...moveis]).filter(Boolean) as {
    tipo: string
    rotulo: string
    texto: string
    icone: string
    href: string
    incerta: boolean
  }[]
})
</script>

<style scoped>
.acoes {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 10px;
  font-size: 12.5px;
}

.acao {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  white-space: nowrap;
}

.acao--whatsapp {
  font-weight: 600;
  color: #15803d;
}

.acao--incerta {
  color: var(--el-text-color-placeholder);
  text-decoration: line-through;
}

.oculto {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
