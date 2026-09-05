package com.denkasir

import com.denkasir.data.local.entity.ProductEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProductImageUploadTest {

    @Test
    fun testProductCreationWithImage() {
        val sampleImagePath = "/data/user/0/com.denkasir/files/product_images/prod_sample_123.jpg"
        val product = ProductEntity(
            id = UUID.randomUUID().toString(),
            name = "Indomie Goreng Spesial",
            barcode = "8998866200213",
            category = "Pcs",
            productType = "RETAIL",
            costPrice = 2500.0,
            sellingPrice = 3500.0,
            stock = 50.0,
            imagePath = sampleImagePath
        )

        assertNotNull(product.imagePath)
        assertEquals(sampleImagePath, product.imagePath)
        assertEquals("Indomie Goreng Spesial", product.name)
        assertEquals(3500.0, product.sellingPrice, 0.001)
    }

    @Test
    fun testProductImageUpdateAndRemoval() {
        var product = ProductEntity(
            id = "prod_001",
            name = "Minyak Goreng 2L",
            barcode = "899275321099",
            category = "Pcs",
            productType = "RETAIL",
            costPrice = 30000.0,
            sellingPrice = 34000.0,
            stock = 15.0,
            imagePath = null
        )

        assertNull(product.imagePath)

        // Simulate user uploading photo
        val uploadedPath = "/data/user/0/com.denkasir/files/product_images/prod_minyak.jpg"
        product = product.copy(imagePath = uploadedPath)
        assertEquals(uploadedPath, product.imagePath)

        // Simulate user removing photo
        product = product.copy(imagePath = null)
        assertNull(product.imagePath)
    }
}
