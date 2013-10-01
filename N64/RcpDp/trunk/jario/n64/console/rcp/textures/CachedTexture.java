/**
 * Copyright 2005, 2013 Jason LaDere
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Originally based on Project64 code.
 *
 */

package jario.n64.console.rcp.textures;

import com.jogamp.common.nio.Buffers;

import jario.hardware.BusDMA;
import jario.hardware.Hardware;

import java.nio.ByteBuffer;
import javax.media.opengl.GL2;

public class CachedTexture
{
	public int[] glName = new int[1];
	public int address;
	public int crc;
	public float offsetS;
	public float offsetT;
	public int maskS;
	public int maskT;
	public int clampS;
	public int clampT;
	public int mirrorS;
	public int mirrorT;
	public int line;
	public int size;
	public int format;
	public int tMem;
	public int palette;
	public int width;
	public int height;
	// N64 width and height
	public int clampWidth;
	public int clampHeight;
	// Size to clamp to
	public int realWidth;
	public int realHeight;
	// Actual texture size
	public float scaleS;
	public float scaleT;
	// Scale to map to 0.0-1.0
	public float shiftScaleS;
	public float shiftScaleT;
	// Scale to shift
	public int textureBytes;
	public CachedTexture lower;
	public CachedTexture higher;

	// public int lastDList;
	// public boolean frameBufferTexture;

	public static CachedTexture getDummy()
	{
		CachedTexture dummy = new CachedTexture();
		dummy.address = 0;
		dummy.clampS = 1;
		dummy.clampT = 1;
		dummy.clampWidth = 2;
		dummy.clampHeight = 2;
		dummy.crc = 0;
		dummy.format = 0;
		dummy.size = 0;
		// dummy.frameBufferTexture = false;
		dummy.width = 2;
		dummy.height = 2;
		dummy.realWidth = 0;
		dummy.realHeight = 0;
		dummy.maskS = 0;
		dummy.maskT = 0;
		dummy.scaleS = 0.5f;
		dummy.scaleT = 0.5f;
		dummy.shiftScaleS = 1.0f;
		dummy.shiftScaleT = 1.0f;
		dummy.textureBytes = 64;
		dummy.tMem = 0;
		return dummy;
	}

	// called by FrameBufferStack, CachedTextureStack
	public final int pow2(int dim)
	{
		int i = 1;
		while (i < dim)
			i <<= 1;
		return i;
	}

	public void load(boolean IA16, int textureBitDepth, ByteBuffer tmem, GL2 gl)
	{
		int glInternalFormat;
		int glType;
		ImageFormat.GetTexelFunc GetTexel;

		if (((ImageFormat.imageFormat[size][format].autoFormat == GL2.GL_RGBA8) ||
				((format == TextureCache.G_IM_FMT_CI) && (IA16)) || (textureBitDepth == 2)) && (textureBitDepth != 0))
		{
			textureBytes = (realWidth * realHeight) << 2;
			if ((format == TextureCache.G_IM_FMT_CI) && (IA16))
			{
				if (size == TextureCache.G_IM_SIZ_4b)
					GetTexel = ImageFormat.GetCI4IA_RGBA8888;
				else
					GetTexel = ImageFormat.GetCI8IA_RGBA8888;

				glInternalFormat = GL2.GL_RGBA8;
				glType = GL2.GL_UNSIGNED_BYTE;
			}
			else
			{
				GetTexel = ImageFormat.imageFormat[size][format].Get32;
				glInternalFormat = ImageFormat.imageFormat[size][format].glInternalFormat32;
				glType = ImageFormat.imageFormat[size][format].glType32;
			}
		}
		else
		{
			textureBytes = (realWidth * realHeight) << 1;
			if ((format == TextureCache.G_IM_FMT_CI) && (IA16))
			{
				if (size == TextureCache.G_IM_SIZ_4b)
					GetTexel = ImageFormat.GetCI4IA_RGBA4444;
				else
					GetTexel = ImageFormat.GetCI8IA_RGBA4444;

				glInternalFormat = GL2.GL_RGBA4;
				glType = GL2.GL_UNSIGNED_SHORT_4_4_4_4;
			}
			else
			{
				GetTexel = ImageFormat.imageFormat[size][format].Get16;
				glInternalFormat = ImageFormat.imageFormat[size][format].glInternalFormat16;
				glType = ImageFormat.imageFormat[size][format].glType16;
			}
		}

		ByteBuffer dest = Buffers.newDirectByteBuffer(textureBytes);
		int newline = line;
		int mirrorSBit;
		int maskSMask;
		int clampSClamp;
		int mirrorTBit;
		int maskTMask;
		int clampTClamp;

		if (size == TextureCache.G_IM_SIZ_32b)
			newline <<= 1;

		if (maskS != 0)
		{
			clampSClamp = clampS != 0 ? clampWidth - 1 : (mirrorS != 0 ? (width << 1) - 1 : width - 1);
			maskSMask = (1 << maskS) - 1;
			mirrorSBit = mirrorS != 0 ? 1 << maskS : 0;
		}
		else
		{
			clampSClamp = StrictMath.min(clampWidth, width) - 1;
			maskSMask = 0xFFFF;
			mirrorSBit = 0x0000;
		}

		if (maskT != 0)
		{
			clampTClamp = clampT != 0 ? clampHeight - 1 : (mirrorT != 0 ? (height << 1) - 1 : height - 1);
			maskTMask = (1 << maskT) - 1;
			mirrorTBit = mirrorT != 0 ? 1 << maskT : 0;
		}
		else
		{
			clampTClamp = StrictMath.min(clampHeight, height) - 1;
			maskTMask = 0xFFFF;
			mirrorTBit = 0x0000;
		}

		ByteBuffer src;
		int i;
		int j = 0;
		int tx;
		int ty;
		for (int y = 0; y < realHeight; y++)
		{
			ty = StrictMath.min(y, clampTClamp) & maskTMask;

			if ((y & mirrorTBit) != 0)
				ty ^= maskTMask;

			tmem.position((tMem + (newline * ty)) * 8);
			src = tmem.slice();

			i = (ty & 1) << 1;
			for (int x = 0; x < realWidth; x++)
			{
				tx = StrictMath.min(x, clampSClamp) & maskSMask;

				if ((x & mirrorSBit) != 0)
					tx ^= maskSMask;

				if (glInternalFormat == GL2.GL_RGBA8)
				{
					dest.asIntBuffer().put(j++, GetTexel.GetTexel(src, tx, i, palette));
				}
				else
				{
					dest.asShortBuffer().put(j++, (short) GetTexel.GetTexel(src, tx, i, palette));
				}
			}
		}

		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, glInternalFormat, realWidth, realHeight, 0, GL2.GL_RGBA, glType, dest);
	}

	public void loadBackground(boolean IA16, int textureBitDepth, Hardware rdram, GL2 gl, int imgwidth, int imgheight, int imgsize, int address)
	{
		int glInternalFormat;
		int glType;
		ImageFormat.GetTexelFunc GetTexel;

		if (((ImageFormat.imageFormat[size][format].autoFormat == GL2.GL_RGBA8) ||
				((format == TextureCache.G_IM_FMT_CI) && (IA16)) || (textureBitDepth == 2)) && (textureBitDepth != 0))
		{
			textureBytes = (realWidth * realHeight) << 2;
			if ((format == TextureCache.G_IM_FMT_CI) && (IA16))
			{
				if (size == TextureCache.G_IM_SIZ_4b)
					GetTexel = ImageFormat.GetCI4IA_RGBA8888;
				else
					GetTexel = ImageFormat.GetCI8IA_RGBA8888;

				glInternalFormat = GL2.GL_RGBA8;
				glType = GL2.GL_UNSIGNED_BYTE;
			}
			else
			{
				GetTexel = ImageFormat.imageFormat[size][format].Get32;
				glInternalFormat = ImageFormat.imageFormat[size][format].glInternalFormat32;
				glType = ImageFormat.imageFormat[size][format].glType32;
			}
		}
		else
		{
			textureBytes = (realWidth * realHeight) << 1;
			if ((format == TextureCache.G_IM_FMT_CI) && (IA16))
			{
				if (size == TextureCache.G_IM_SIZ_4b)
					GetTexel = ImageFormat.GetCI4IA_RGBA4444;
				else
					GetTexel = ImageFormat.GetCI8IA_RGBA4444;

				glInternalFormat = GL2.GL_RGBA4;
				glType = GL2.GL_UNSIGNED_SHORT_4_4_4_4;
			}
			else
			{
				GetTexel = ImageFormat.imageFormat[size][format].Get16;
				glInternalFormat = ImageFormat.imageFormat[size][format].glInternalFormat16;
				glType = ImageFormat.imageFormat[size][format].glType16;
			}
		}

		int bpl = imgwidth << imgsize >> 1;
		int numBytes = bpl * imgheight;
		ByteBuffer swapped = ByteBuffer.allocate(numBytes);

		((BusDMA) rdram).readDMA(address, swapped, 0, numBytes);

		ByteBuffer dest = ByteBuffer.allocateDirect(textureBytes);
		ByteBuffer src;

		int clampSClamp = width - 1;
		int clampTClamp = height - 1;

		int tx;
		int ty;
		for (int y = 0; y < realHeight; y++)
		{
			ty = StrictMath.min(y, clampTClamp);

			swapped.position(bpl * ty);
			src = swapped.slice();

			for (int x = 0; x < realWidth; x++)
			{
				tx = StrictMath.min(x, clampSClamp);

				if (glInternalFormat == GL2.GL_RGBA8)
					dest.putInt(GetTexel.GetTexel(src, tx, 0, palette));
				else
					dest.putShort((short) GetTexel.GetTexel(src, tx, 0, palette));
			}
		}

		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, glInternalFormat, realWidth, realHeight, 0, GL2.GL_RGBA, glType, dest);
	}

	public void activate(float uls, float ult, int shifts, int shiftt, float scaleX, float scaleY)
	{
		scaleS = scaleX / (float) realWidth;
		scaleT = scaleY / (float) realHeight;

		if (shifts > 10)
			shiftScaleS = (float) (1 << (16 - shifts));
		else if (shifts > 0)
			shiftScaleS = 1.0f / (float) (1 << shifts);
		else
			shiftScaleS = 1.0f;

		if (shiftt > 10)
			shiftScaleT = (float) (1 << (16 - shiftt));
		else if (shiftt > 0)
			shiftScaleT = 1.0f / (float) (1 << shiftt);
		else
			shiftScaleT = 1.0f;

		offsetS = uls;
		offsetT = ult;
	}
}
