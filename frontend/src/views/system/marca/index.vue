<!-- Rota: /system/identidade-visual · Permissão: infra:marca:update -->
<template>
  <ContentWrap>
    <div class="flex flex-wrap gap-32px">
      <el-form
        ref="formRef"
        v-loading="carregando"
        :model="form"
        :rules="regras"
        class="max-w-460px flex-1"
        label-position="top"
      >
        <el-form-item label="Nome do sistema" prop="nome">
          <el-input v-model="form.nome" maxlength="60" show-word-limit />
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            Aparece no menu, na aba do navegador e na tela de entrada.
          </div>
        </el-form-item>
        <el-form-item label="Logo">
          <UploadImg
            v-model="form.logo"
            :file-size="1"
            :file-type="['image/png', 'image/jpeg', 'image/webp']"
            directory="marca"
            height="120px"
            width="120px"
          >
            <template #tip>PNG, JPG ou WebP quadrado, de até 1 MB.</template>
          </UploadImg>
          <div class="mt-6px w-full text-12px text-[var(--el-text-color-secondary)]">
            Sem logo enviada, vale a do CO-TI. Ela também vira o ícone da aba.
          </div>
        </el-form-item>
        <el-form-item label="Texto da tela de login">
          <el-input
            v-model="form.frase"
            :autosize="{ minRows: 2, maxRows: 5 }"
            :placeholder="FRASE_PADRAO"
            maxlength="300"
            show-word-limit
            type="textarea"
          />
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            Aparece abaixo do "Bem-vindo"; cada linha vira uma linha na tela. Vazio usa o texto
            padrão.
          </div>
        </el-form-item>
        <el-form-item label="Foto da tela de login">
          <UploadImg
            v-model="form.fundo"
            :file-size="3"
            :file-type="['image/jpeg', 'image/webp', 'image/png']"
            directory="marca"
            height="120px"
            width="180px"
          >
            <template #tip>JPG ou WebP na horizontal, de até 3 MB (ideal 1600 × 1000).</template>
          </UploadImg>
          <div class="mt-6px w-full text-12px text-[var(--el-text-color-secondary)]">
            Ocupa o lado esquerdo da entrada; sem foto enviada, vale a padrão. A parte de baixo é
            escurecida para o texto ficar legível.
          </div>
        </el-form-item>
        <el-form-item>
          <el-button
            v-hasPermi="['infra:marca:update']"
            :loading="salvando"
            type="primary"
            @click="salvar"
            >Salvar</el-button
          >
          <el-button v-hasPermi="['infra:marca:update']" @click="padrao"
            >Voltar ao padrão</el-button
          >
        </el-form-item>
      </el-form>

      <div class="min-w-280px">
        <div class="mb-8px text-13px font-medium">Como fica</div>
        <div class="previa">
          <div class="previa__menu">
            <img :src="form.logo || logoPadrao" alt="" class="h-32px w-32px object-contain" />
            <span class="ml-10px truncate text-15px font-700 text-white">{{
              form.nome || 'Centro de Operações de TI'
            }}</span>
          </div>
          <div
            class="previa__foto"
            :style="{ backgroundImage: `url(${form.fundo || fundoPadrao})` }"
          >
            <div class="text-white">
              <div class="text-14px font-bold">Bem-vindo</div>
              <div class="whitespace-pre-line text-11px opacity-90">{{
                form.frase || FRASE_PADRAO
              }}</div>
            </div>
          </div>
          <div class="previa__login">
            <img :src="form.logo || logoPadrao" alt="" class="h-40px w-40px object-contain" />
            <span class="ml-10px truncate text-17px font-bold">{{
              form.nome || 'Centro de Operações de TI'
            }}</span>
          </div>
        </div>
      </div>
    </div>
  </ContentWrap>
</template>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import { getMarca, salvarMarca, type Marca } from '@/api/system/marca'
import { FRASE_PADRAO, useMarcaStore } from '@/store/modules/marca'
import logoPadrao from '@/assets/imgs/coti.svg'
import fundoPadrao from '@/assets/imgs/login-fundo.jpg'

defineOptions({ name: 'SystemMarca' })

const NOME_PADRAO = 'Centro de Operações de TI'
const message = useMessage()
const marca = useMarcaStore()
const formRef = ref<FormInstance>()
const form = reactive<Marca>({ nome: '', logo: '', fundo: '', frase: '' })
const carregando = ref(true)
const salvando = ref(false)
const regras: FormRules = {
  nome: [{ required: true, whitespace: true, message: 'Informe o nome', trigger: 'blur' }]
}

const padrao = () => {
  form.nome = NOME_PADRAO
  form.logo = ''
  form.fundo = ''
  form.frase = ''
}

const salvar = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  salvando.value = true
  try {
    const nova = {
      nome: form.nome.trim(),
      logo: form.logo || '',
      fundo: form.fundo || '',
      frase: form.frase.trim()
    }
    await salvarMarca(nova)
    marca.aplicar(nova)
    message.success('Identidade visual salva')
  } finally {
    salvando.value = false
  }
}

onMounted(async () => {
  try {
    Object.assign(form, await getMarca())
  } finally {
    carregando.value = false
  }
})
</script>

<style lang="scss" scoped>
.previa {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  &__menu {
    display: flex;
    height: 50px;
    padding: 0 14px;
    background: var(--left-menu-bg-color, #001529);
    align-items: center;
  }

  &__foto {
    display: flex;
    height: 130px;
    padding: 12px 14px;
    background-position: center;
    background-size: cover;
    align-items: flex-end;
    box-shadow: inset 0 -60px 40px -20px rgb(8 20 34 / 75%);
  }

  &__login {
    display: flex;
    padding: 22px 14px;
    background: var(--el-bg-color);
    align-items: center;
  }
}
</style>
