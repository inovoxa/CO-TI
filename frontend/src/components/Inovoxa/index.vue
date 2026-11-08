<!-- Assinatura da Inovoxa, dona do CO-TI: fica no login e no rodapé, independente da identidade da instância -->
<template>
  <a
    class="inovoxa inline-flex items-center gap-6px text-12px text-[var(--el-text-color-secondary)] decoration-none hover:text-[var(--el-text-color-primary)]"
    href="https://inovoxa.com.br"
    rel="noopener"
    target="_blank"
    title="inovoxa.com.br"
  >
    Desenvolvido por <img :src="logo" alt="Inovoxa" class="inovoxa__logo h-16px" />
  </a>
</template>

<script lang="ts" setup>
import logo from '@/assets/imgs/inovoxa.png'

defineOptions({ name: 'InovoxaAssinatura' })
</script>

<style lang="scss" scoped>
// o texto da logo é azul-marinho: no tema escuro inverte a luz mantendo as cores
html.dark .inovoxa__logo {
  filter: invert(1) hue-rotate(180deg);
}
</style>
