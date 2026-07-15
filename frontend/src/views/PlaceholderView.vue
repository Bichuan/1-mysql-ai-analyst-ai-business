<script setup>
defineProps({
  title: String,
  description: String
})
</script>

<template>
  <section class="placeholder-page">
    <p class="eyebrow">COMING NEXT</p>
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
  </section>
</template>
