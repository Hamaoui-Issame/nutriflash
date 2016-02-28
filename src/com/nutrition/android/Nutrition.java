package com.nutrition.android;

import android.graphics.Bitmap;

/**
 * 
 * @author Issame
 *
 */
public class Nutrition {

	private String error;
	private String product_name;
	private Bitmap image;
	private String fat_level;
	private String fat_weight;
	private String saturated_fat_level;
	private String saturated_fat_weight;
	private String sugars_level;
	private String sugars_weight;
	private String salt_level;
	private String salt_weight;
	private String energy_unit;
	private String energy_100g;
	private String data_per;

	public Nutrition(String error) {
		super();
		this.error = error;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getProduct_name() {
		return product_name;
	}

	public void setProduct_name(String product_name) {
		this.product_name = product_name;
	}

	public Bitmap getImage() {
		return image;
	}

	public void setImage(Bitmap image) {
		this.image = image;
	}

	public String getFat_level() {
		return fat_level;
	}

	public void setFat_level(String fat_level) {
		this.fat_level = fat_level;
	}

	public String getFat_weight() {
		return fat_weight;
	}

	public void setFat_weight(String fat_weight) {
		this.fat_weight = fat_weight;
	}

	public String getSaturated_fat_level() {
		return saturated_fat_level;
	}

	public void setSaturated_fat_level(String saturated_fat_level) {
		this.saturated_fat_level = saturated_fat_level;
	}

	public String getSaturated_fat_weight() {
		return saturated_fat_weight;
	}

	public void setSaturated_fat_weight(String saturated_fat_weight) {
		this.saturated_fat_weight = saturated_fat_weight;
	}

	public String getSugars_level() {
		return sugars_level;
	}

	public void setSugars_level(String sugars_level) {
		this.sugars_level = sugars_level;
	}

	public String getSugars_weight() {
		return sugars_weight;
	}

	public void setSugars_weight(String sugars_weight) {
		this.sugars_weight = sugars_weight;
	}

	public String getSalt_level() {
		return salt_level;
	}

	public void setSalt_level(String salt_level) {
		this.salt_level = salt_level;
	}

	public String getSalt_weight() {
		return salt_weight;
	}

	public void setSalt_weight(String salt_weight) {
		this.salt_weight = salt_weight;
	}

	public String getEnergy_unit() {
		return energy_unit;
	}

	public void setEnergy_unit(String energy_unit) {
		this.energy_unit = energy_unit;
	}

	public String getEnergy_100g() {
		return energy_100g;
	}

	public void setEnergy_100g(String energy_100g) {
		this.energy_100g = energy_100g;
	}

	public String getData_per() {
		return data_per;
	}

	public void setData_per(String data_per) {
		this.data_per = data_per;
	}

}
