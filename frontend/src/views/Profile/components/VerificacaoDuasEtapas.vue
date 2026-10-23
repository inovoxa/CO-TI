<template>
  <div v-loading="carregando" class="max-w-520px">
    <!-- Ativa -->
    <template v-if="status?.ativo">
      <el-alert :closable="false" show-icon type="success" :title="t('coti.mfa.ativa')" />
      <p class="mt-12px">
        {{ t('coti.mfa.codigosRestantes', { n: status.codigosRecuperacaoRestantes }) }}
      </p>
      <el-alert
        v-if="status.codigosRecuperacaoRestantes <= 3"
        :closable="false"
        class="mt-8px"
        show-icon
        type="warning"
        :title="t('coti.mfa.poucosCodigos')"
      />
      <el-form class="mt-16px" inline @submit.prevent>
        <el-form-item :label="t('coti.mfa.codigoAtual')">
          <el-input
            v-model="codigoDesativar"
            autocomplete="one-time-code"
            maxlength="20"
            :placeholder="t('coti.mfa.codigoPlaceholder')"
          />
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!codigoDesativar" type="danger" @click="desativar">
            {{ t('coti.mfa.desativar') }}
          </el-button>
        </el-form-item>
      </el-form>
    </template>

    <!-- Inativa, sem cadastro em andamento -->
    <template v-else-if="!cadastro">
      <el-alert :closable="false" show-icon type="info" :title="t('coti.mfa.inativa')" />
      <p class="mt-12px">{{ t('coti.mfa.explicacao') }}</p>
      <el-button class="mt-12px" type="primary" @click="iniciar">{{ t('coti.mfa.ativar') }}</el-button>
    </template>

    <!-- Cadastro em andamento -->
    <template v-else>
      <ol class="pl-18px leading-7">
        <li>{{ t('coti.mfa.passo1') }}</li>
        <li>{{ t('coti.mfa.passo2') }}</li>
      </ol>
      <img v-if="qrCode" :src="qrCode" alt="QR code" class="my-12px h-200px w-200px" />
      <p class="text-12px text-[var(--el-text-color-secondary)]">
        {{ t('coti.mfa.semCamera') }} <code class="select-all">{{ segredoFormatado }}</code>
      </p>
      <el-form class="mt-12px" inline @submit.prevent>
        <el-form-item :label="t('coti.mfa.passo3')">
          <el-input
            v-model="codigoConfirmar"
            autocomplete="one-time-code"
            inputmode="numeric"
            maxlength="6"
            placeholder="000000"
            @keyup.enter="confirmar"
          />
        </el-form-item>
        <el-form-item>
          <el-button :disabled="codigoConfirmar.length !== 6" type="primary" @click="confirmar">
            {{ t('coti.mfa.confirmar') }}
          </el-button>
          <el-button @click="cadastro = undefined">{{ t('common.cancel') }}</el-button>
        </el-form-item>
      </el-form>
    </template>

    <!-- Códigos de recuperação: só aparecem uma vez -->
    <el-dialog
      v-model="mostrarCodigos"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      :title="t('coti.mfa.codigosTitulo')"
      width="460px"
    >
      <p>{{ t('coti.mfa.codigosExplicacao') }}</p>
      <div class="my-12px grid grid-cols-2 gap-6px font-mono text-15px">
        <span v-for="c in codigos" :key="c">{{ c }}</span>
      </div>
      <template #footer>
        <el-button @click="baixarCodigos">{{ t('coti.mfa.baixar') }}</el-button>
        <el-button @click="copiarCodigos">{{ t('coti.mfa.copiar') }}</el-button>
        <el-button type="primary" @click="fecharCodigos">{{ t('coti.mfa.guardei') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import QRCode from 'qrcode'
import * as IdentidadeApi from '@/api/identidade'

defineOptions({ name: 'VerificacaoDuasEtapas' })

const { t } = useI18n()
const message = useMessage()

const carregando = ref(false)
const status = ref<IdentidadeApi.MfaStatus>()
const cadastro = ref<IdentidadeApi.MfaCadastro>()
const qrCode = ref('')
const codigoConfirmar = ref('')
const codigoDesativar = ref('')
const codigos = ref<string[]>([])
const mostrarCodigos = ref(false)

const segredoFormatado = computed(() => (cadastro.value?.segredo || '').replace(/(.{4})/g, '$1 ').trim())

const carregar = async () => {
  carregando.value = true
  try {
    status.value = await IdentidadeApi.getMfaStatus()
  } finally {
    carregando.value = false
  }
}

const iniciar = async () => {
  carregando.value = true
  try {
    cadastro.value = await IdentidadeApi.iniciarMfa()
    qrCode.value = await QRCode.toDataURL(cadastro.value.uri, { margin: 1, width: 200 })
    codigoConfirmar.value = ''
  } finally {
    carregando.value = false
  }
}

const confirmar = async () => {
  if (codigoConfirmar.value.length !== 6) return
  carregando.value = true
  try {
    codigos.value = await IdentidadeApi.confirmarMfa(codigoConfirmar.value)
    cadastro.value = undefined
    qrCode.value = ''
    mostrarCodigos.value = true
    await carregar()
  } finally {
    carregando.value = false
  }
}

const desativar = async () => {
  await message.confirm(t('coti.mfa.confirmarDesativar'))
  carregando.value = true
  try {
    await IdentidadeApi.desativarMfa(codigoDesativar.value.trim())
    codigoDesativar.value = ''
    message.success(t('coti.mfa.desativada'))
    await carregar()
  } finally {
    carregando.value = false
  }
}

const textoCodigos = () => codigos.value.join('\n') + '\n'

const copiarCodigos = async () => {
  await navigator.clipboard.writeText(textoCodigos())
  message.success(t('common.copySuccess'))
}

const baixarCodigos = () => {
  const blob = new Blob([textoCodigos()], { type: 'text/plain' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'codigos-recuperacao-co-ti.txt'
  a.click()
  URL.revokeObjectURL(a.href)
}

const fecharCodigos = () => {
  mostrarCodigos.value = false
  codigos.value = []
}

onMounted(carregar)
</script>
